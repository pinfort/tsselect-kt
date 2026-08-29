package me.pinfort.tsselect

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

// Port of tsdump(): analyse a transport stream and return its statistics.
//
// Convenience entry point: opens and closes the file itself. Throws
// TsSourceOpenException when the file cannot be read and TsFormatException
// when it is not a transport stream.
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

// Primary entry point. The caller owns the stream. totalBytes is only used to
// compute the fraction handed to the progress listener; pass 0 when the size
// is unknown. Each call analyses the stream from scratch.
public fun tsDump(
    input: InputStream,
    totalBytes: Long,
    progress: ProgressListener = ProgressListener.NONE,
): TsDumpReport {
    val engine = TsDumpEngine()
    engine.run(input, totalBytes, progress)
    return engine.report()
}
