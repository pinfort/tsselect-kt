package me.pinfort.tsselect

import java.io.InputStream
import java.util.Arrays

// Caps both how many resync events tsDump() will detail and, per event, how
// many drops it lists (the latter capped independently at 4, see
// addDropInfo). The C original uses the same two fixed-size arrays for the
// same reason: a pathological stream can resync or drop thousands of times,
// and the report is meant to show the first handful as a diagnostic sample,
// not to be a complete log. resyncCount/ResyncReport.dropCount still track
// the true totals even once the detailed lists stop growing, so a caller can
// tell "8 resyncs, only 8 shown" from "8 resyncs, all shown".
internal const val RESYNC_LOG_MAX = 8

// The dump-mode state machine. Internal: callers use the tsDump() functions,
// which construct a fresh engine per run so statistics can never accumulate
// across two runs.
internal class TsDumpEngine {
    val stats = Array(PidSelection.PID_COUNT) { TsStatus(it) }
    val resyncReports = Array(RESYNC_LOG_MAX) { ResyncReport() }
    var resyncCount = 0
        private set

    private val header = TsHeader()
    private val adapt = AdaptationField()

    fun run(
        input: InputStream,
        totalBytes: Long,
        progress: ProgressListener,
    ) {
        val buf = ByteArray(8192)
        var offset = 0L
        var idx = 0
        var n = readFully(input, buf, 0, buf.size)

        val unitSize = selectUnitSize(buf, n)
        if (unitSize < 188) {
            throw TsFormatException()
        }

        var curr: Int
        do {
            curr = 0
            val tail = n
            while (curr + unitSize < tail) {
                if (buf[curr] != SYNC_BYTE || buf[curr + unitSize] != SYNC_BYTE) {
                    if (resyncCount < RESYNC_LOG_MAX) {
                        resyncReports[resyncCount].miss = offset + curr
                    }
                    val p = resync(buf, curr, tail, unitSize)
                    if (p < 0) {
                        break
                    }
                    curr = p
                    if (resyncCount < RESYNC_LOG_MAX) {
                        resyncReports[resyncCount].sync = offset + curr
                    }
                    resyncCount += 1
                    if (curr + unitSize > tail) {
                        break
                    }
                }
                processPacket(buf, curr, offset + curr)
                curr += unitSize
            }

            offset += curr

            progress.onProgress(Progress(idx, offset, totalBytes, finished = false))
            idx += 1

            n = tail - curr
            if (n > 0) {
                System.arraycopy(buf, curr, buf, 0, n)
            }
            val m = readFully(input, buf, n, buf.size - n)
            if (m < 1) {
                break
            }
            n += m
        } while (n > unitSize)

        curr = 0
        while (curr + 188 <= n) {
            if (buf[curr] != SYNC_BYTE) {
                if (resyncCount < RESYNC_LOG_MAX) {
                    resyncReports[resyncCount].miss = offset + curr
                }
                val p = resyncForce(buf, curr, n, unitSize)
                if (p < 0) {
                    break
                }
                curr = p
                if (resyncCount < RESYNC_LOG_MAX) {
                    resyncReports[resyncCount].sync = offset + curr
                }
                resyncCount += 1
                if (p + 188 > n) {
                    break
                }
            }
            processPacket(buf, curr, offset + curr)
            curr += unitSize
        }

        progress.onProgress(Progress(idx, offset, totalBytes, finished = true))
    }

    // Consolidated packet-processing body (C duplicates it at lines 250-303
    // and 346-398 of tsselect.c).
    //
    // Decision tree for whether a packet counts as a "drop" (a continuity
    // error), following MPEG-2's per-PID continuity_counter (CC) rule: a
    // payload-bearing packet's CC must be exactly one more than the previous
    // packet on the same PID, mod 16; a packet with no payload must repeat
    // the previous CC unchanged; and a PID may resend one packet verbatim
    // (CC unchanged) to signal a deliberate duplicate, but never two in a
    // row. Checked in this order, once lcc (the previous CC) is known and
    // discontinuityIndicator is *not* set (that flag means the encoder is
    // telling us a break is expected, so skip the check entirely):
    //   1. pid == 0x1fff (the null PID): stuffing packets have no CC
    //      semantics, so never flagged.
    //   2. no payload (adaptationFieldControl bit 0 clear): CC must not
    //      change; any change is a drop.
    //   3. lcc == CC (same counter as last time): either a legitimate single
    //      retransmission (payload bytes identical) or the start of one -
    //      duplicateCount tracks how many of these in a row we've seen, and
    //      a second one in a row is itself a drop, not just a differing
    //      payload.
    //   4. otherwise (CC advanced): must have advanced by exactly 1 mod 16,
    //      or it's a drop; duplicateCount resets since we left the "same CC"
    //      run.
    fun processPacket(
        buf: ByteArray,
        pos: Int,
        filePos: Long,
    ) {
        header.parse(buf, pos)
        if (header.adaptationFieldControl and 2 != 0) {
            adapt.parse(buf, pos + 4)
        } else {
            adapt.clear()
        }

        val pid = header.pid
        val st = stats[pid]
        if (st.first < 0) {
            st.first = filePos
        }
        val lcc = st.lastContinuityCounter
        if (lcc >= 0 && adapt.discontinuityIndicator == 0) {
            if (pid == 0x1fff) {
                // null packet - drop count has no mean
                // do nothing
            } else if (header.adaptationFieldControl and 0x01 == 0) {
                // no payload : continuity_counter should not increment
                if (lcc != header.continuityCounter) {
                    st.drop += 1
                    addDropInfo(pid, filePos)
                }
            } else if (lcc == header.continuityCounter) {
                // has payload and same continuity_counter
                if (!Arrays.equals(st.lastPacket, 0, 188, buf, pos, pos + 188)) {
                    // non-duplicate packet
                    st.drop += 1
                    addDropInfo(pid, filePos)
                }
                st.duplicateCount += 1
                if (st.duplicateCount > 1) {
                    // duplicate packet count exceeds limit (two)
                    st.drop += 1
                    addDropInfo(pid, filePos)
                }
            } else {
                val expected = (lcc + 1) and 0x0f
                if (expected != header.continuityCounter) {
                    st.drop += 1
                    addDropInfo(pid, filePos)
                }
                st.duplicateCount = 0
            }
        }
        st.lastContinuityCounter = header.continuityCounter
        st.total += 1
        if (header.transportErrorIndicator != 0) {
            st.error += 1
        }
        System.arraycopy(buf, pos, st.lastPacket, 0, 188)
        if (header.transportScramblingControl != 0) {
            st.scrambling += 1
        }
    }

    // Attributes a drop to resyncReports[resyncCount - 1], i.e. the *previous*
    // resync entry, not "entry number resyncCount". resyncCount already counts
    // the resync that just happened (run() increments it right after
    // recording sync), so by the time a packet after that recovery turns out
    // to be dropped, resyncCount - 1 is the index of the entry describing the
    // very loss-of-sync/recovery pair this drop occurred after. A drop found
    // before the first resync (resyncCount == 0) has no entry to attach to
    // and is silently uncounted here, matching the C original: the top-level
    // per-PID drop counter (TsStatus.drop) still records it regardless.
    private fun addDropInfo(
        pid: Int,
        pos: Long,
    ) {
        val idx = resyncCount - 1
        if (idx !in 0..<RESYNC_LOG_MAX) {
            // do nothing
            return
        }

        val report = resyncReports[idx]
        if (report.dropCount < 4) {
            val n = report.dropCount.toInt()
            report.dropPid[n] = pid
            report.dropPos[n] = pos
        }

        report.dropCount += 1
    }

    // Snapshot of the collected statistics. Mirrors what the C tsselect prints:
    // only PIDs that saw at least one packet, resync entries capped at
    // RESYNC_LOG_MAX, and at most four recorded drops per resync entry.
    fun report(): TsDumpReport {
        val entries =
            resyncReports.take(minOf(resyncCount, RESYNC_LOG_MAX)).map { r ->
                val drops =
                    (0 until minOf(r.dropCount, 4L).toInt()).map { j ->
                        DropEntry(r.dropPid[j], r.dropPos[j])
                    }
                ResyncEntry(r.miss, r.sync, r.dropCount, drops)
            }

        val pids =
            stats.filter { it.total > 0 }.map {
                PidReport(it.pid, it.total, it.drop, it.error, it.scrambling, it.first)
            }

        return TsDumpReport(resyncCount, entries, pids)
    }
}
