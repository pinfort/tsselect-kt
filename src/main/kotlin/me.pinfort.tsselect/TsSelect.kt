package me.pinfort.tsselect

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale

// Port of tsselect(): remux mode. Writes exactly 188 bytes per selected-PID
// packet (strips TS-192/204 trailers). pidMap[pid] != 0 selects the PID.
fun tsSelect(src: String, dst: String, pidMap: ByteArray) {
    val srcFile = File(src)
    val input = try {
        FileInputStream(srcFile)
    } catch (_: IOException) {
        System.err.print("error - failed on open(%s) [src]\n".format(Locale.ROOT, src))
        return
    }
    input.use { ins ->
        val output = try {
            BufferedOutputStream(FileOutputStream(dst), 65536)
        } catch (_: IOException) {
            System.err.print("error - failed on open(%s) [dst]\n".format(Locale.ROOT, dst))
            return
        }
        try {
            copySelected(ins, output, srcFile.length(), pidMap)
        } finally {
            // a failed write was already reported inside copySelected;
            // C ignores _close errors, so swallow them here too
            try {
                output.close()
            } catch (_: IOException) {
            }
        }
    }
}

private fun copySelected(input: FileInputStream, output: OutputStream, total: Long, pidMap: ByteArray) {
    val header = TsHeader()

    val buf = ByteArray(8192)
    var offset = 0L
    var idx = 0
    var n = readFully(input, buf, 0, buf.size)

    val unitSize = selectUnitSize(buf, n)
    if (unitSize < 188) {
        System.err.print("error - failed on select_unit_size()\n")
        return
    }

    var curr: Int
    try {
        do {
            curr = 0
            val tail = n
            while (curr + unitSize < tail) {
                if (buf[curr] != SYNC_BYTE || buf[curr + unitSize] != SYNC_BYTE) {
                    val p = resync(buf, curr, tail, unitSize)
                    if (p < 0) {
                        break
                    }
                    curr = p
                    if (curr + unitSize > tail) {
                        break
                    }
                }
                header.parse(buf, curr)
                if (pidMap[header.pid].toInt() != 0) {
                    output.write(buf, curr, 188)
                }
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
            val m = readFully(input, buf, n, buf.size - n)
            if (m < 1) {
                break
            }
            n += m
        } while (n > unitSize)

        curr = 0
        while (curr + 188 <= n) {
            if (buf[curr] != SYNC_BYTE) {
                val p = resyncForce(buf, curr, n, unitSize)
                if (p < 0) {
                    break
                }
                curr = p
                if (p + 188 > n) {
                    break
                }
            }
            header.parse(buf, curr)
            if (pidMap[header.pid].toInt() != 0) {
                output.write(buf, curr, 188)
            }
            curr += unitSize
        }

        output.flush()
    } catch (_: IOException) {
        System.err.print("error - failed on write() [dst]\n")
        return
    }

    System.err.print("\rprocessing: finish\n")
}
