package me.pinfort.tsselect

internal const val SYNC_BYTE: Byte = 0x47

// Port of select_unit_size: histogram of 0x47-to-0x47 strides in [188, 320),
// returns the most frequent one, or 0 when the buffer does not validate.
//
// Why a histogram: a lone 0x47 byte proves nothing (it is one byte in 256,
// so plain payload will produce plenty of false positives at every stride).
// What is diagnostic is a *repeating* stride: real TS/M2TS/RS packets put a
// sync byte at the same offset every unit_size bytes, so the correct stride
// is the one that recurs far more often than chance predicts.
//
// Why the two checks in step 3:
//   - `m < 8`: fewer than 8 hits for the winning stride is indistinguishable
//     from noise on a buffer this size, so refuse to guess.
//   - `w + 2*n < len` (w = m*n): the winning stride must plausibly tile the
//     whole buffer - m sync bytes n bytes apart span roughly w bytes, and
//     that has to account for most of the len bytes examined, not just a
//     short run at the start.
// Either failing means "not a transport stream" (TsFormatException upstream).
internal fun selectUnitSize(
    buf: ByteArray,
    len: Int,
): Int {
    val count = IntArray(320 - 188)

    // 1st step, count up 0x47 interval
    var pos = 0
    while (pos + 188 < len) {
        if (buf[pos] != SYNC_BYTE) {
            pos += 1
            continue
        }
        var m = 320
        if (pos + m > len) {
            m = len - pos
        }
        for (i in 188 until m) {
            if (buf[pos + i] == SYNC_BYTE) {
                count[i - 188] += 1
            }
        }
        pos += 1
    }

    // 2nd step, select maximum appeared interval
    var m = 0
    var n = 0
    for (i in 188 until 320) {
        if (m < count[i - 188]) {
            m = count[i - 188]
            n = i
        }
    }

    // 3rd step, verify unit_size
    val w = m * n
    if (m < 8 || w + 2 * n < len) {
        return 0
    }

    return n
}

// Port of resync: find 8 consecutive sync bytes at unit_size stride.
// Returns the index of the recovered sync position, or -1.
//
// Why 8 and not fewer: this runs mid-buffer, where there is always more data
// ahead to check, so it can afford to demand strong evidence before
// declaring the stream realigned - a false resync here would silently
// misparse every packet after it for the rest of the chunk. resyncForce
// below relaxes this same idea for the one place a run out of buffer to
// check is unavoidable: the tail of the last chunk.
internal fun resync(
    buf: ByteArray,
    from: Int,
    len: Int,
    unitSize: Int,
): Int {
    var pos = from
    val limit = len - unitSize * 8
    while (pos < limit) {
        if (buf[pos] == SYNC_BYTE) {
            var i = 1
            while (i < 8) {
                if (buf[pos + unitSize * i] != SYNC_BYTE) {
                    break
                }
                i += 1
            }
            if (i == 8) {
                return pos
            }
        }
        pos += 1
    }
    return -1
}

// Port of resync_force: relaxed variant for the buffer tail — every remaining
// unit_size stride position must be a sync byte.
//
// Why relaxed: near the end of the final chunk there may be fewer than 8
// more unit_size strides left before EOF, so resync's fixed threshold of 8
// can never be satisfied even for a perfectly aligned stream. resyncForce
// demands agreement from every stride that *does* fit (n = however many
// remain), down to n == 0, which accepts the position outright when not
// even one full stride remains to check.
internal fun resyncForce(
    buf: ByteArray,
    from: Int,
    len: Int,
    unitSize: Int,
): Int {
    var pos = from
    while (pos < len - 188) {
        if (buf[pos] == SYNC_BYTE) {
            val n = (len - pos) / unitSize
            if (n == 0) {
                return pos
            }
            var i = 1
            while (i < n) {
                if (buf[pos + unitSize * i] != SYNC_BYTE) {
                    break
                }
                i += 1
            }
            if (i == n) {
                return pos
            }
        }
        pos += 1
    }
    return -1
}
