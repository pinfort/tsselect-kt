package me.pinfort.tsselect

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val OUTPUT_BUFFER_SIZE = 65536

/**
 * Port of `tsselect()`: remux mode. Writes exactly 188 bytes per selected-PID
 * packet, stripping TS-192/204 trailers/headers regardless of the detected
 * grid.
 *
 * Convenience entry point: opens and closes both [src] and [dst] itself.
 * [src] is opened before [dst], and the two opens are **not** interchangeable:
 * a source that cannot be read leaves [dst] untouched, exactly as the C
 * original, which jumps past the destination open on a source failure. A
 * format failure, by contrast, is only detected after [dst] is already open,
 * so it *does* leave an empty [dst] behind.
 *
 * @param src the transport stream to read.
 * @param dst the file to remux into; truncated/created as by
 *   [java.io.FileOutputStream].
 * @param pids the PIDs to keep (or drop, if built with `exclude = true`).
 * @param progress notified once per read chunk, then once more with
 *   [Progress.finished] set.
 * @return the detected unit size and packet counters.
 * @throws TsSourceOpenException if [src] cannot be opened for reading.
 * @throws TsDestinationOpenException if [dst] cannot be opened for writing.
 * @throws TsFormatException if the input is not a 188/192/204-byte packet
 *   stream.
 * @throws TsWriteException if a write to [dst] fails.
 */
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

/**
 * Primary entry point. The caller owns both [input] and [output]; this
 * function never closes either, though it does [OutputStream.flush] `output`
 * before returning successfully.
 *
 * @param input the transport stream to read.
 * @param output the destination to remux into.
 * @param totalBytes the stream's total size, used only to compute
 *   [Progress.basisPoints]; pass 0 when the size is unknown.
 * @param pids the PIDs to keep (or drop, if built with `exclude = true`).
 * @param progress notified once per read chunk, then once more with
 *   [Progress.finished] set.
 * @return the detected unit size and packet counters.
 * @throws TsFormatException if the input is not a 188/192/204-byte packet
 *   stream.
 * @throws TsWriteException if a write to [output] fails.
 */
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
