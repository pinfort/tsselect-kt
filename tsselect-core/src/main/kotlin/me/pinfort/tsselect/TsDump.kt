package me.pinfort.tsselect

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Arrays

const val RESYNC_LOG_MAX = 8

class TsDump {
    val stats = Array(8192) { TsStatus(it) }
    val resyncReports = Array(RESYNC_LOG_MAX) { ResyncReport() }
    var resyncCount = 0
        private set

    private val header = TsHeader()
    private val adapt = AdaptationField()

    // Convenience entry point: opens and closes the file itself. Propagates
    // FileNotFoundException when the file cannot be read.
    fun dump(file: File, progress: ProgressListener = ProgressListener.NONE) {
        FileInputStream(file).use { ins ->
            dump(ins, file.length(), progress)
        }
    }

    // Primary entry point. The caller owns the stream. totalBytes is only used
    // to compute the fraction handed to the progress listener.
    fun dump(input: InputStream, totalBytes: Long, progress: ProgressListener = ProgressListener.NONE) {
        val buf = ByteArray(8192)
        var offset = 0L
        var idx = 0
        var n = readFully(input, buf, 0, buf.size)

        val unitSize = selectUnitSize(buf, n)
        if (unitSize < 188) {
            throw TsFormatException("failed on select_unit_size()")
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

            progress.onProgress(offset, totalBytes)
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

        progress.onFinish()
    }

    // Consolidated packet-processing body (C duplicates it at lines 250-303
    // and 346-398 of tsselect.c).
    fun processPacket(buf: ByteArray, pos: Int, filePos: Long) {
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

    private fun addDropInfo(pid: Int, pos: Long) {
        val idx = resyncCount - 1
        if (idx >= RESYNC_LOG_MAX || idx < 0) {
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
        val entries = ArrayList<ResyncEntry>()
        for (i in 0 until minOf(resyncCount, RESYNC_LOG_MAX)) {
            val r = resyncReports[i]
            val drops = ArrayList<DropEntry>()
            for (j in 0 until minOf(r.dropCount, 4L).toInt()) {
                drops.add(DropEntry(r.dropPid[j], r.dropPos[j]))
            }
            entries.add(ResyncEntry(r.miss, r.sync, r.dropCount, drops))
        }

        val pids = ArrayList<PidReport>()
        for (i in 0 until 8192) {
            val st = stats[i]
            if (st.total > 0) {
                pids.add(PidReport(i, st.total, st.drop, st.error, st.scrambling, st.first))
            }
        }

        return TsDumpReport(resyncCount, entries, pids)
    }
}
