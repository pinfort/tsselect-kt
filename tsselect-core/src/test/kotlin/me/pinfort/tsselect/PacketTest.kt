package me.pinfort.tsselect

import kotlin.test.Test
import kotlin.test.assertEquals

class PacketTest {
    @Test
    fun parsesTsHeaderFields() {
        val packet = tsPacket(pid = 0x1abc, cc = 0x0d, afc = 3, tei = true, scrambling = 2)
        val header = TsHeader()
        header.parse(packet, 0)

        assertEquals(0x47, header.sync)
        assertEquals(1, header.transportErrorIndicator)
        assertEquals(0, header.payloadUnitStartIndicator)
        assertEquals(0, header.transportPriority)
        assertEquals(0x1abc, header.pid)
        assertEquals(2, header.transportScramblingControl)
        assertEquals(3, header.adaptationFieldControl)
        assertEquals(0x0d, header.continuityCounter)
    }

    @Test
    fun parsesDiscontinuityIndicator() {
        val af = AdaptationField()
        af.parse(byteArrayOf(1, 0x80.toByte()), 0)
        assertEquals(1, af.discontinuityIndicator)
        assertEquals(1, af.adaptationFieldLength)
    }

    @Test
    fun parsesPcrWithCFaithfulBitPacking() {
        // length=7, pcr_flag, PCR bytes 00 00 00 01 80 01:
        // base (1 shl 10) or ((0x80 and 0x80) shl 2) or 0x01 = 0x400 or 0x201
        val af = AdaptationField()
        af.parse(byteArrayOf(7, 0x10, 0x00, 0x00, 0x00, 0x01, 0x80.toByte(), 0x01), 0)
        assertEquals(1, af.pcrFlag)
        assertEquals(0x601L, af.programClockReference)
    }

    @Test
    fun truncatedAdaptationFieldClearsStruct() {
        // pcr_flag set but only 1 byte of AF: malformed, struct must be zeroed.
        val af = AdaptationField()
        af.parse(byteArrayOf(1, 0x10), 0)
        assertEquals(0, af.pcrFlag)
        assertEquals(0, af.adaptationFieldLength)
        assertEquals(0L, af.programClockReference)
    }

    @Test
    fun zeroLengthAdaptationFieldClearsStruct() {
        val af = AdaptationField()
        af.discontinuityIndicator = 1
        af.parse(byteArrayOf(0, 0x7f), 0)
        assertEquals(0, af.discontinuityIndicator)
        assertEquals(0, af.adaptationFieldLength)
    }
}
