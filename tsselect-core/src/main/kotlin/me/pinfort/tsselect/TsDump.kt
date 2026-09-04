package me.pinfort.tsselect

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Port of `tsdump()`: analyse a transport stream and return its statistics.
 *
 * Convenience entry point: opens and closes [file] itself.
 *
 * @param file the transport stream to analyse.
 * @param progress notified once per read chunk, then once more with
 *   [Progress.finished] set; see [ProgressListener].
 * @return the collected per-PID and resync statistics.
 * @throws TsSourceOpenException if [file] cannot be opened for reading.
 * @throws TsFormatException if the input is not a 188/192/204-byte packet
 *   stream, i.e. [selectUnitSize] cannot find a valid grid.
 */
public fun tsDump(
    file: File,
    progress: ProgressListener = ProgressListener.NONE,
): TsDumpReport {
    val input =
        try {
            FileInputStream(file)
        } catch (e: IOException) {
            throw TsSourceOpenException(file.path, e)
        }
    try {
        return tsDump(input, file.length(), progress)
    } finally {
        // C ignores _close errors, so swallow them here too
        try {
            input.close()
        } catch (_: IOException) {
        }
    }
}

/**
 * Primary entry point. The caller owns [input]; this function never closes it.
 * Each call analyses the stream from scratch with a fresh [TsDumpEngine].
 *
 * @param input the transport stream to analyse.
 * @param totalBytes the stream's total size, used only to compute
 *   [Progress.basisPoints]; pass 0 when the size is unknown (e.g. a FIFO or
 *   `/dev/stdin`).
 * @param progress notified once per read chunk, then once more with
 *   [Progress.finished] set.
 * @return the collected per-PID and resync statistics.
 * @throws TsFormatException if the input is not a 188/192/204-byte packet
 *   stream, i.e. [selectUnitSize] cannot find a valid grid.
 */
public fun tsDump(
    input: InputStream,
    totalBytes: Long,
    progress: ProgressListener = ProgressListener.NONE,
): TsDumpReport {
    val engine = TsDumpEngine()
    engine.run(input, totalBytes, progress)
    return engine.report()
}
