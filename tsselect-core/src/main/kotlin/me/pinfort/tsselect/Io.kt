package me.pinfort.tsselect

import java.io.IOException
import java.io.InputStream

// C's _read on a regular file fills the whole buffer; InputStream.read may
// return short reads, so loop until the requested range is full or EOF.
// A read error is treated like EOF (C breaks the chunk loop on _read < 1).
fun readFully(
    input: InputStream,
    buf: ByteArray,
    off: Int,
    len: Int,
): Int {
    var got = 0
    while (got < len) {
        val r =
            try {
                input.read(buf, off + got, len - got)
            } catch (_: IOException) {
                break
            }
        if (r < 0) {
            break
        }
        got += r
    }
    return got
}
