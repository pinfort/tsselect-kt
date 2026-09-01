package me.pinfort.tsselect

/**
 * Builds a synthetic TS packet.
 *
 * @param adaptation raw adaptation-field bytes (length byte first) placed at
 *        offset 4 when afc has the adaptation bit set
 * @param size 188 (TS), 192 (M2TS, trailing timestamp) or 204 (trailing RS)
 */
fun tsPacket(
    pid: Int,
    cc: Int,
    afc: Int = 1,
    tei: Boolean = false,
    scrambling: Int = 0,
    payloadFill: Byte = 0,
    adaptation: ByteArray? = null,
    size: Int = 188,
): ByteArray {
    val packet = ByteArray(size)
    packet[0] = 0x47
    packet[1] = (((if (tei) 1 else 0) shl 7) or ((pid shr 8) and 0x1f)).toByte()
    packet[2] = (pid and 0xff).toByte()
    packet[3] = ((scrambling shl 6) or (afc shl 4) or (cc and 0x0f)).toByte()
    for (i in 4 until 188) {
        packet[i] = payloadFill
    }
    adaptation?.copyInto(packet, 4)
    return packet
}

fun stream(vararg packets: ByteArray): ByteArray {
    val out = ByteArray(packets.sumOf { it.size })
    var off = 0
    for (p in packets) {
        p.copyInto(out, off)
        off += p.size
    }
    return out
}
