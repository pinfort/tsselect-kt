package me.pinfort.tsselect

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// Port of tsselect(): remux mode. Writes exactly 188 bytes per selected-PID
// packet (strips TS-192/204 trailers). pidMap[pid] != 0 selects the PID.
//
// Convenience entry point: opens and closes both files itself. Propagates
// FileNotFoundException when either file cannot be opened, and IOException
// when a write fails.
fun tsSelect(
    src: File,
    dst: File,
    pidMap: ByteArray,
    progress: ProgressListener = ProgressListener.NONE,
) {
    src.inputStream().use { input ->
        val output = BufferedOutputStream(FileOutputStream(dst), 65536)
        try {
            tsSelect(input, output, src.length(), pidMap, progress)
        } finally {
            // C ignores _close errors, so swallow them here too
            try {
                output.close()
            } catch (_: IOException) {
            }
        }
    }
}

// Primary entry point. The caller owns both streams. totalBytes is only used
// to compute the fraction handed to the progress listener.
fun tsSelect(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long,
    pidMap: ByteArray,
    progress: ProgressListener = ProgressListener.NONE,
) {
    val header = TsHeader()

    val buf = ByteArray(8192)
    var offset = 0L
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

        progress.onProgress(offset, totalBytes)

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

    progress.onFinish()
}
