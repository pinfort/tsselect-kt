package me.pinfort.tsselect

const val SYNC_BYTE: Byte = 0x47

// Port of select_unit_size: histogram of 0x47-to-0x47 strides in [188, 320),
// returns the most frequent one, or 0 when the buffer does not validate.
fun selectUnitSize(buf: ByteArray, len: Int): Int {
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
fun resync(buf: ByteArray, from: Int, len: Int, unitSize: Int): Int {
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
fun resyncForce(buf: ByteArray, from: Int, len: Int, unitSize: Int): Int {
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
