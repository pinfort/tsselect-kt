package me.pinfort.tsselect

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Arrays
import java.util.Locale

const val RESYNC_LOG_MAX = 8

class TsDump {
    val stats = Array(8192) { TsStatus(it) }
    val resyncReports = Array(RESYNC_LOG_MAX) { ResyncReport() }
    var resyncCount = 0
        private set

    private val header = TsHeader()
    private val adapt = AdaptationField()

    fun run(path: String) {
        dump(path)
        printReports()
    }

    private fun dump(path: String) {
        val file = File(path)
        val input = try {
            FileInputStream(file)
        } catch (_: IOException) {
            System.err.print("error - failed on open(%s) [src]\n".format(Locale.ROOT, path))
            return
        }
        input.use { ins ->
            val total = file.length()

            val buf = ByteArray(8192)
            var offset = 0L
            var idx = 0
            var n = readFully(ins, buf, 0, buf.size)

            val unitSize = selectUnitSize(buf, n)
            if (unitSize < 188) {
                System.err.print("error - failed on select_unit_size()\n")
                return
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

                if (idx and 0x0f == 0) {
                    val pct = (10000L * offset / total).toInt()
                    System.err.print("\rprocessing: %2d.%02d%%".format(Locale.ROOT, pct / 100, pct % 100))
                }
                idx += 1

                n = tail - curr
                if (n > 0) {
                    System.arraycopy(buf, curr, buf, 0, n)
                }
                val m = readFully(ins, buf, n, buf.size - n)
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

            System.err.print("\rprocessing: finish\n")
        }
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

    fun printReports() {
        if (resyncCount > 0) {
            printResyncReport()
        }

        for (i in 0 until 8192) {
            if (stats[i].total > 0) {
                print(
                    "pid=0x%04x, total=%8d, d=%3d, e=%3d, scrambling=%d, offset=%d\n".format(
                        Locale.ROOT, i, stats[i].total, stats[i].drop, stats[i].error,
                        stats[i].scrambling, stats[i].first
                    )
                )
            }
        }
    }

    private fun printResyncReport() {
        print("total sync error: %d\n".format(Locale.ROOT, resyncCount))

        var m = resyncCount
        if (m > RESYNC_LOG_MAX) {
            m = RESYNC_LOG_MAX
        }

        for (i in 0 until m) {
            val report = resyncReports[i]
            print(
                "  resync[%d] : miss=0x%012x, sync=0x%012x, drop=%d\n".format(
                    Locale.ROOT, i, report.miss, report.sync, report.dropCount
                )
            )
            var n = report.dropCount.toInt()
            if (n > 4) {
                n = 4
            }
            for (j in 0 until n) {
                print(
                    "    drop[%d] : pid=0x%04x, pos=0x%012x\n".format(
                        Locale.ROOT, j, report.dropPid[j], report.dropPos[j]
                    )
                )
            }
        }
    }
}
