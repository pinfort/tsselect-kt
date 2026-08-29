package me.pinfort.tsselect

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val OUTPUT_BUFFER_SIZE = 65536

// Port of tsselect(): remux mode. Writes exactly 188 bytes per selected-PID
// packet (strips TS-192/204 trailers).
//
// Convenience entry point: opens and closes both files itself. src is opened
// before dst, so a source that cannot be read leaves dst untouched - as in the
// C original, which jumps past the destination open on a source failure.
// Distinguishes the two opens by exception type so a caller can report which
// one failed without doing the opening itself.
public fun tsSelect(
    src: File,
    dst: File,
    pids: PidSelection,
    progress: ProgressListener = ProgressListener.NONE,
): TsSelectResult {
    val input =
        try {
            FileInputStream(src)
        } catch (e: IOException) {
            throw TsSourceOpenException(src.path, e)
        }
    try {
        val output =
            try {
                BufferedOutputStream(FileOutputStream(dst), OUTPUT_BUFFER_SIZE)
            } catch (e: IOException) {
                throw TsDestinationOpenException(dst.path, e)
            }
        try {
            return tsSelect(input, output, src.length(), pids, progress)
        } finally {
            // C ignores _close errors, so swallow them here too
            try {
                output.close()
            } catch (_: IOException) {
            }
        }
    } finally {
        try {
            input.close()
        } catch (_: IOException) {
        }
    }
}

// Primary entry point. The caller owns both streams. totalBytes is only used
// to compute the fraction handed to the progress listener; pass 0 when the
// size is unknown.
public fun tsSelect(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long,
    pids: PidSelection,
    progress: ProgressListener = ProgressListener.NONE,
): TsSelectResult {
    val header = TsHeader()

    val buf = ByteArray(8192)
    var offset = 0L
    var idx = 0
    var packetsRead = 0L
    var packetsWritten = 0L
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
            packetsRead += 1
            if (header.pid in pids) {
                output.writePacket(buf, curr)
                packetsWritten += 1
            }
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
        packetsRead += 1
        if (header.pid in pids) {
            output.writePacket(buf, curr)
            packetsWritten += 1
        }
        curr += unitSize
    }

    try {
        output.flush()
    } catch (e: IOException) {
        throw TsWriteException(e)
    }

    progress.onProgress(Progress(idx, offset, totalBytes, finished = true))

    return TsSelectResult(unitSize, packetsRead, packetsWritten)
}

// Writes one 188-byte packet, reporting a write failure as TsWriteException so
// the only IOException a caller can see from a remux is a destination write.
private fun OutputStream.writePacket(
    buf: ByteArray,
    off: Int,
) {
    try {
        write(buf, off, 188)
    } catch (e: IOException) {
        throw TsWriteException(e)
    }
}
