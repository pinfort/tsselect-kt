package me.pinfort.tsselect

// Bit-field readers. The ISO/IEC 13818-1 tables specify TS fields as bit
// positions inside a byte, so these take positions and derive the mask,
// rather than spelling out a hand-computed literal at every call site.
private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xff

private fun ByteArray.bit(
    i: Int,
    n: Int,
): Int = (u8(i) shr n) and 1

// Bits [hi..lo] of byte i, hi and lo counted from the MSB end as bit 7..0
// and both inclusive - so bits(i, 4, 0) is the low 5 bits.
private fun ByteArray.bits(
    i: Int,
    hi: Int,
    lo: Int,
): Int = (u8(i) shr lo) and ((1 shl (hi - lo + 1)) - 1)

// A 188-byte packet minus the 4-byte header and the length byte itself: an
// adaptation_field_length past this cannot fit, so the field is malformed.
private const val MAX_ADAPTATION_FIELD_LENGTH = 188 - 4 - 1

// Encoded sizes of the optional sub-fields, used both to bounds-check before
// reading one and to step past it afterwards.
private const val PCR_BYTES = 6
private const val LTW_BYTES = 2
private const val PIECEWISE_RATE_BYTES = 3
private const val SEAMLESS_SPLICE_BYTES = 5

internal fun TsHeader.parse(
    buf: ByteArray,
    off: Int,
) {
    sync = buf.u8(off)
    transportErrorIndicator = buf.bit(off + 1, 7)
    payloadUnitStartIndicator = buf.bit(off + 1, 6)
    transportPriority = buf.bit(off + 1, 5)
    pid = (buf.bits(off + 1, 4, 0) shl 8) or buf.u8(off + 2)
    transportScramblingControl = buf.bits(off + 3, 7, 6)
    adaptationFieldControl = buf.bits(off + 3, 5, 4)
    continuityCounter = buf.bits(off + 3, 3, 0)
}

// PCR/OPCR field layout (ISO/IEC 13818-1): each packs a 33-bit base (90kHz
// clock) and a 9-bit extension (27MHz clock; PCR = base*300 + extension)
// into 6 bytes where the sub-fields are not byte-aligned - the base's low
// bit and the extension both live inside a shared byte alongside
// reserved/marker bits. The parsing below builds the 33-bit base by
// combining 4 whole bytes with one more bit pulled out of the 5th byte, then
// shifts left again to make room for the extension bits, which are pulled
// out of that same 5th byte plus the 6th.
//
// The exact bit positions this reproduces are the C source's, including its
// odd low-bits packing; kept as raw literals rather than rewritten against
// the spec's field names or expressed with bits() above, since naming these
// shifts would imply they mean something in the spec. The two are worth
// cross-checking against each other, not merged.
private fun readProgramClockReference(
    buf: ByteArray,
    p: Int,
): Long {
    var pcr = ((buf.u8(p) shl 24) or (buf.u8(p + 1) shl 16) or (buf.u8(p + 2) shl 8) or buf.u8(p + 3)).toLong()
    pcr = pcr shl 10
    pcr = pcr or (((buf.u8(p + 4) and 0x80) shl 2) or ((buf.u8(p + 4) and 1) shl 1) or buf.u8(p + 5)).toLong()
    return pcr
}

// DTS_next_AU: a single 33-bit timestamp split into 3/15/15-bit chunks by
// marker bits (unlike PCR/OPCR above, there is no separate extension clock
// here) - reassembled by shifting each chunk into place as it's read. Raw
// literals for the same reason as readProgramClockReference.
private fun readDtsNextAu(
    buf: ByteArray,
    p: Int,
): Long {
    var dts = (((buf.u8(p) and 0x0e) shl 14) or (buf.u8(p + 1) shl 7) or ((buf.u8(p + 2) shr 1) and 0x7f)).toLong()
    dts = dts shl 15
    dts = dts or ((buf.u8(p + 3) shl 7) or ((buf.u8(p + 4) shr 1) and 0x7f)).toLong()
    return dts
}

// Port of extract_adaptation_field: any malformed field zeroes the whole
// struct and returns, exactly like the C original.
internal fun AdaptationField.parse(
    buf: ByteArray,
    off: Int,
) {
    clear()
    val length = buf.u8(off)
    if (length == 0 || length > MAX_ADAPTATION_FIELD_LENGTH) {
        return
    }

    adaptationFieldLength = length
    var p = off + 1
    val tail = p + length
    if (p + 1 > tail) {
        clear()
        return
    }

    discontinuityIndicator = buf.bit(p, 7)
    randomAccessIndicator = buf.bit(p, 6)
    elementaryStreamPriorityIndicator = buf.bit(p, 5)
    pcrFlag = buf.bit(p, 4)
    opcrFlag = buf.bit(p, 3)
    splicingPointFlag = buf.bit(p, 2)
    transportPrivateDataFlag = buf.bit(p, 1)
    adaptationFieldExtensionFlag = buf.bit(p, 0)

    p += 1

    if (pcrFlag != 0) {
        if (p + PCR_BYTES > tail) {
            clear()
            return
        }
        programClockReference = readProgramClockReference(buf, p)
        p += PCR_BYTES
    }

    if (opcrFlag != 0) {
        if (p + PCR_BYTES > tail) {
            clear()
            return
        }
        originalProgramClockReference = readProgramClockReference(buf, p)
        p += PCR_BYTES
    }

    if (splicingPointFlag != 0) {
        if (p + 1 > tail) {
            clear()
            return
        }
        spliceCountdown = buf.u8(p)
        p += 1
    }

    if (transportPrivateDataFlag != 0) {
        if (p + 1 > tail) {
            clear()
            return
        }
        val n = buf.u8(p)
        transportPrivateDataLength = n
        p += 1 + n
        if (p > tail) {
            clear()
            return
        }
    }

    if (adaptationFieldExtensionFlag != 0) {
        // The extension's own length byte plus the byte of presence flags.
        if (p + 2 > tail) {
            clear()
            return
        }
        var n = buf.u8(p)
        adaptationFieldExtensionLength = n
        p += 1
        if (p + n > tail) {
            clear()
            return
        }
        ltwFlag = buf.bit(p, 7)
        piecewiseRateFlag = buf.bit(p, 6)
        seamlessSpliceFlag = buf.bit(p, 5)
        p += 1
        n -= 1
        if (ltwFlag != 0) {
            if (n < LTW_BYTES) {
                clear()
                return
            }
            ltwValidFlag = buf.bit(p, 7)
            ltwOffset = (buf.bits(p, 6, 0) shl 8) or buf.u8(p + 1)
            p += LTW_BYTES
            n -= LTW_BYTES
        }
        if (piecewiseRateFlag != 0) {
            if (n < PIECEWISE_RATE_BYTES) {
                clear()
                return
            }
            piecewiseRate = (buf.bits(p, 5, 0) shl 16) or (buf.u8(p + 1) shl 8) or buf.u8(p + 2)
            p += PIECEWISE_RATE_BYTES
            n -= PIECEWISE_RATE_BYTES
        }
        if (seamlessSpliceFlag != 0) {
            if (n < SEAMLESS_SPLICE_BYTES) {
                clear()
                return
            }
            spliceType = buf.bits(p, 7, 4)
            dtsNextAu = readDtsNextAu(buf, p)
        }
    }
}
