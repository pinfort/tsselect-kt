package me.pinfort.tsselect

import java.io.IOException

// Raised when the input does not look like an MPEG-2 TS stream, i.e. when
// selectUnitSize() cannot find a 188/192/204-byte packet grid.
class TsFormatException(
    message: String,
) : IOException(message)
