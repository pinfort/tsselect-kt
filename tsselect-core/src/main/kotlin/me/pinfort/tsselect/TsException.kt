package me.pinfort.tsselect

import java.io.IOException

// Every failure the library signals is one of these four, mirroring the four
// error paths of the C original. The public entry points never throw a bare
// IOException: file-open and write failures are wrapped, so a caller can pick
// its diagnostic from the type alone instead of guessing from context.
//
// There is deliberately no read failure: readFully() treats a read error as
// EOF, exactly as the C code treats _read < 1, so a failed read is not
// observable through this API.
public sealed class TsException(
    message: String,
    cause: Throwable?,
) : IOException(message, cause)

// The source could not be opened. path is the source as it was given to the
// library; callers that print argv should print argv, since java.io.File
// normalises paths and the C tool does not.
public class TsSourceOpenException(
    public val path: String,
    cause: IOException,
) : TsException("failed to open source: $path", cause)

// The destination could not be opened or created.
public class TsDestinationOpenException(
    public val path: String,
    cause: IOException,
) : TsException("failed to open destination: $path", cause)

// The input is not a transport stream: select_unit_size() found no 188/192/204
// byte packet grid.
public class TsFormatException : TsException("failed on select_unit_size()", null)

// A write to the destination failed.
public class TsWriteException(
    cause: IOException,
) : TsException("failed to write destination", cause)
