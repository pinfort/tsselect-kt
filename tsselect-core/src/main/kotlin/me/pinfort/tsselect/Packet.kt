package me.pinfort.tsselect

private fun ByteArray.at(i: Int): Int = this[i].toInt() and 0xff

internal fun TsHeader.parse(
    buf: ByteArray,
    off: Int,
) {
    sync = buf.at(off)
    transportErrorIndicator = (buf.at(off + 1) shr 7) and 0x01
    payloadUnitStartIndicator = (buf.at(off + 1) shr 6) and 0x01
    transportPriority = (buf.at(off + 1) shr 5) and 0x01
    pid = ((buf.at(off + 1) and 0x1f) shl 8) or buf.at(off + 2)
    transportScramblingControl = (buf.at(off + 3) shr 6) and 0x03
    adaptationFieldControl = (buf.at(off + 3) shr 4) and 0x03
    continuityCounter = buf.at(off + 3) and 0x0f
}

// Port of extract_adaptation_field: any malformed field zeroes the whole
// struct and returns, exactly like the C original.
//
// PCR/OPCR field layout (ISO/IEC 13818-1): each packs a 33-bit base (90kHz
// clock) and a 9-bit extension (27MHz clock; PCR = base*300 + extension)
// into 6 bytes where the sub-fields are not byte-aligned - the base's low
// bit and the extension both live inside a shared byte alongside
// reserved/marker bits. The parsing below builds the 33-bit base by
// combining 4 whole bytes with one more bit pulled out of the 5th byte, then
// shifts left again to make room for the extension bits, which are pulled
// out of that same 5th byte plus the 6th. The exact bit positions this
// reproduces are the C source's; kept as-is rather than rewritten against
// the spec's field names, since the two are worth cross-checking against
// each other, not merged. dtsNextAu (below, in the seamless_splice branch)
// is structurally different - a plain 33-bit timestamp chunked by marker
// bits into three pieces, with no separate extension clock.
internal fun AdaptationField.parse(
    buf: ByteArray,
    off: Int,
) {
    clear()
    val length = buf.at(off)
    if (length == 0 || length > 183) {
        return
    }

    adaptationFieldLength = length
    var p = off + 1
    val tail = p + length
    if (p + 1 > tail) {
        clear()
        return
    }

    discontinuityIndicator = (buf.at(p) shr 7) and 1
    randomAccessIndicator = (buf.at(p) shr 6) and 1
    elementaryStreamPriorityIndicator = (buf.at(p) shr 5) and 1
    pcrFlag = (buf.at(p) shr 4) and 1
    opcrFlag = (buf.at(p) shr 3) and 1
    splicingPointFlag = (buf.at(p) shr 2) and 1
    transportPrivateDataFlag = (buf.at(p) shr 1) and 1
    adaptationFieldExtensionFlag = buf.at(p) and 1

    p += 1

    if (pcrFlag != 0) {
        if (p + 6 > tail) {
            clear()
            return
        }
        var pcr = ((buf.at(p) shl 24) or (buf.at(p + 1) shl 16) or (buf.at(p + 2) shl 8) or buf.at(p + 3)).toLong()
        pcr = pcr shl 10
        // Bit-faithful to the C source, including its odd low-bits packing.
        pcr = pcr or (((buf.at(p + 4) and 0x80) shl 2) or ((buf.at(p + 4) and 1) shl 1) or buf.at(p + 5)).toLong()
        programClockReference = pcr
        p += 6
    }

    if (opcrFlag != 0) {
        if (p + 6 > tail) {
            clear()
            return
        }
        var opcr = ((buf.at(p) shl 24) or (buf.at(p + 1) shl 16) or (buf.at(p + 2) shl 8) or buf.at(p + 3)).toLong()
        opcr = opcr shl 10
        opcr = opcr or (((buf.at(p + 4) and 0x80) shl 2) or ((buf.at(p + 4) and 1) shl 1) or buf.at(p + 5)).toLong()
        originalProgramClockReference = opcr
        p += 6
    }

    if (splicingPointFlag != 0) {
        if (p + 1 > tail) {
            clear()
            return
        }
        spliceCountdown = buf.at(p)
        p += 1
    }

    if (transportPrivateDataFlag != 0) {
        if (p + 1 > tail) {
            clear()
            return
        }
        val n = buf.at(p)
        transportPrivateDataLength = n
        p += 1 + n
        if (p > tail) {
            clear()
            return
        }
    }

    if (adaptationFieldExtensionFlag != 0) {
        if (p + 2 > tail) {
            clear()
            return
        }
        var n = buf.at(p)
        adaptationFieldExtensionLength = n
        p += 1
        if (p + n > tail) {
            clear()
            return
        }
        ltwFlag = (buf.at(p) shr 7) and 1
        piecewiseRateFlag = (buf.at(p) shr 6) and 1
        seamlessSpliceFlag = (buf.at(p) shr 5) and 1
        p += 1
        n -= 1
        if (ltwFlag != 0) {
            if (n < 2) {
                clear()
                return
            }
            ltwValidFlag = (buf.at(p) shr 7) and 1
            ltwOffset = ((buf.at(p) and 0x7f) shl 8) or buf.at(p + 1)
            p += 2
            n -= 2
        }
        if (piecewiseRateFlag != 0) {
            if (n < 3) {
                clear()
                return
            }
            piecewiseRate = ((buf.at(p) and 0x3f) shl 16) or (buf.at(p + 1) shl 8) or buf.at(p + 2)
            p += 3
            n -= 3
        }
        if (seamlessSpliceFlag != 0) {
            if (n < 5) {
                clear()
                return
            }
            spliceType = (buf.at(p) shr 4) and 0x0f
            // DTS_next_AU: a single 33-bit timestamp split into 3/15/15-bit
            // chunks by marker bits (unlike PCR/OPCR above, there is no
            // separate extension clock here) - reassembled by shifting each
            // chunk into place as it's read.
            var dts = (((buf.at(p) and 0x0e) shl 14) or (buf.at(p + 1) shl 7) or ((buf.at(p + 2) shr 1) and 0x7f)).toLong()
            dts = dts shl 15
            dts = dts or ((buf.at(p + 3) shl 7) or ((buf.at(p + 4) shr 1) and 0x7f)).toLong()
            dtsNextAu = dts
        }
    }
}
