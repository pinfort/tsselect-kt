package me.pinfort.tsselect

const val PID_COUNT = 8192

// Builds the ByteArray(8192) selection map tsSelect() takes. PIDs outside
// 0..8191 are ignored (as the C argument loop does); exclude=true inverts the
// map so the listed PIDs are the ones dropped.
fun pidMapOf(
    pids: Collection<Int>,
    exclude: Boolean = false,
): ByteArray {
    val map = ByteArray(PID_COUNT)
    for (pid in pids) {
        if (pid in 0 until PID_COUNT) {
            map[pid] = 1
        }
    }

    if (exclude) {
        for (i in 0 until PID_COUNT) {
            map[i] = if (map[i].toInt() == 0) 1 else 0
        }
    }

    return map
}
