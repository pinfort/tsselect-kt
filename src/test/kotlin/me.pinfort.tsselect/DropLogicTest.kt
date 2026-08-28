package me.pinfort.tsselect

import kotlin.test.Test
import kotlin.test.assertEquals

class DropLogicTest {

    private val pid = 0x100

    private fun feed(dump: TsDump, vararg packets: ByteArray) {
        packets.forEachIndexed { i, p ->
            dump.processPacket(p, 0, 188L * i)
        }
    }

    @Test
    fun normalSequenceHasNoDrops() {
        val dump = TsDump()
        feed(dump, tsPacket(pid, 0), tsPacket(pid, 1), tsPacket(pid, 2))
        assertEquals(0L, dump.stats[pid].drop)
        assertEquals(3L, dump.stats[pid].total)
    }

    @Test
    fun continuityGapCountsOneDrop() {
        val dump = TsDump()
        feed(dump, tsPacket(pid, 0), tsPacket(pid, 2))
        assertEquals(1L, dump.stats[pid].drop)
    }

    @Test
    fun continuityWrapsFrom15ToZero() {
        val dump = TsDump()
        feed(dump, tsPacket(pid, 15), tsPacket(pid, 0))
        assertEquals(0L, dump.stats[pid].drop)
    }

    @Test
    fun singleIdenticalDuplicateIsNotADrop() {
        val dump = TsDump()
        val p = tsPacket(pid, 0)
        feed(dump, p, p)
        assertEquals(0L, dump.stats[pid].drop)
        assertEquals(1, dump.stats[pid].duplicateCount)
    }

    @Test
    fun secondIdenticalDuplicateCountsOneDrop() {
        val dump = TsDump()
        val p = tsPacket(pid, 0)
        feed(dump, p, p, p)
        assertEquals(1L, dump.stats[pid].drop)
        assertEquals(2, dump.stats[pid].duplicateCount)
    }

    @Test
    fun sameCcDifferentPayloadCountsOneDrop() {
        val dump = TsDump()
        feed(dump, tsPacket(pid, 0, payloadFill = 0), tsPacket(pid, 0, payloadFill = 1))
        assertEquals(1L, dump.stats[pid].drop)
    }

    @Test
    fun duplicateCountResetsWhenCcAdvances() {
        val dump = TsDump()
        val p = tsPacket(pid, 0)
        feed(dump, p, p, tsPacket(pid, 1), tsPacket(pid, 1))
        assertEquals(0L, dump.stats[pid].drop)
        assertEquals(1, dump.stats[pid].duplicateCount)
    }

    @Test
    fun discontinuityIndicatorSuppressesDropCheck() {
        val dump = TsDump()
        feed(
            dump,
            tsPacket(pid, 0),
            tsPacket(pid, 5, afc = 3, adaptation = byteArrayOf(1, 0x80.toByte())),
        )
        assertEquals(0L, dump.stats[pid].drop)
    }

    @Test
    fun nullPacketPidNeverDrops() {
        val dump = TsDump()
        feed(dump, tsPacket(0x1fff, 0), tsPacket(0x1fff, 7))
        assertEquals(0L, dump.stats[0x1fff].drop)
        assertEquals(2L, dump.stats[0x1fff].total)
    }

    @Test
    fun noPayloadContinuityChangeIsADrop() {
        val dump = TsDump()
        feed(
            dump,
            tsPacket(pid, 0, afc = 2, adaptation = byteArrayOf(1, 0)),
            tsPacket(pid, 1, afc = 2, adaptation = byteArrayOf(1, 0)),
        )
        assertEquals(1L, dump.stats[pid].drop)
    }

    @Test
    fun noPayloadSameContinuityIsNotADrop() {
        val dump = TsDump()
        feed(
            dump,
            tsPacket(pid, 0, afc = 2, adaptation = byteArrayOf(1, 0)),
            tsPacket(pid, 0, afc = 2, adaptation = byteArrayOf(1, 0)),
        )
        assertEquals(0L, dump.stats[pid].drop)
    }

    @Test
    fun errorAndScramblingCountersAccumulate() {
        val dump = TsDump()
        feed(
            dump,
            tsPacket(pid, 0, tei = true, scrambling = 2),
            tsPacket(pid, 1, tei = true),
            tsPacket(pid, 2),
        )
        assertEquals(2L, dump.stats[pid].error)
        assertEquals(1L, dump.stats[pid].scrambling)
        assertEquals(0L, dump.stats[pid].drop)
    }
}
