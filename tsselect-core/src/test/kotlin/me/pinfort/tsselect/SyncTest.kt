package me.pinfort.tsselect

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncTest {
    private fun packets(
        count: Int,
        size: Int,
    ): ByteArray = stream(*Array(count) { tsPacket(pid = 0x100, cc = it and 0x0f, size = size) })

    @Test
    fun selectsUnitSize188() {
        val buf = packets(10, 188)
        assertEquals(188, selectUnitSize(buf, buf.size))
    }

    @Test
    fun selectsUnitSize192() {
        val buf = packets(10, 192)
        assertEquals(192, selectUnitSize(buf, buf.size))
    }

    @Test
    fun selectsUnitSize204() {
        val buf = packets(10, 204)
        assertEquals(204, selectUnitSize(buf, buf.size))
    }

    @Test
    fun rejectsTooFewPackets() {
        // 8 packets yield only 7 counted intervals; the m >= 8 check fails.
        val buf = packets(8, 188)
        assertEquals(0, selectUnitSize(buf, buf.size))
    }

    @Test
    fun rejectsGarbage() {
        // no sync bytes at all: the histogram stays empty
        val buf = ByteArray(4096) { 0x55 }
        assertEquals(0, selectUnitSize(buf, buf.size))
    }

    @Test
    fun resyncFindsSyncAfterGarbage() {
        val garbage = ByteArray(100) { 0x11 }
        val buf = stream(garbage, packets(10, 188))
        assertEquals(100, resync(buf, 0, buf.size, 188))
    }

    @Test
    fun resyncNeedsEightConsecutiveSyncs() {
        val garbage = ByteArray(50) { 0x11 }
        // Only 7 packets after the garbage: never 8 syncs in a row.
        val buf = stream(garbage, packets(7, 188))
        assertEquals(-1, resync(buf, 0, buf.size, 188))
    }

    @Test
    fun resyncForceFindsShortTail() {
        val garbage = ByteArray(10) { 0x11 }
        val buf = stream(garbage, packets(2, 188))
        assertEquals(10, resyncForce(buf, 0, buf.size, 188))
    }

    @Test
    fun resyncForceGivesUpWithoutSync() {
        val buf = ByteArray(400) { 0x11 }
        assertEquals(-1, resyncForce(buf, 0, buf.size, 188))
    }
}
