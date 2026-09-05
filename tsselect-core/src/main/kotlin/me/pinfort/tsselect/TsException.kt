package me.pinfort.tsselect

import java.io.IOException

/**
 * Every failure the library signals is one of these four subclasses,
 * mirroring the four error paths of the C original. The public entry points
 * ([tsDump], [tsSelect]) never throw a bare [IOException]: file-open and
 * write failures are wrapped, so a caller can `when`-exhaust the sealed
 * hierarchy and pick its diagnostic from the type alone instead of guessing
 * from context (see `errorMessage` in the CLI module for exactly that).
 *
 * There is deliberately no read-failure case: `readFully()` treats a read
 * error as EOF, exactly as the C code treats `_read < 1`, so a failed read is
 * not observable through this API.
 */
public sealed class TsException(
    message: String,
    cause: Throwable?,
) : IOException(message, cause)

/**
 * The source could not be opened for reading.
 *
 * @property path the source path as it was given to the library. Callers
 *   that print argv should print argv rather than this property when the two
 *   might differ, since [java.io.File] normalises paths (e.g. `dir//file` to
 *   `dir/file`) and the C tool does not.
 */
public class TsSourceOpenException(
    public val path: String,
    cause: IOException,
) : TsException("failed to open source: $path", cause)

/**
 * The destination could not be opened or created.
 *
 * @property path the destination path as it was given to the library; see
 *   [TsSourceOpenException.path] for why callers may prefer their own copy of
 *   this string over this property.
 */
public class TsDestinationOpenException(
    public val path: String,
    cause: IOException,
) : TsException("failed to open destination: $path", cause)

/**
 * The input is not a transport stream: [selectUnitSize] (C's
 * `select_unit_size()`) found no valid 188/192/204-byte packet grid.
 */
public class TsFormatException : TsException("failed on select_unit_size()", null)

/** A write to the destination failed. */
public class TsWriteException(
    cause: IOException,
) : TsException("failed to write destination", cause)
