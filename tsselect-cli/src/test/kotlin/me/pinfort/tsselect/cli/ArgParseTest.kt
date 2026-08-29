package me.pinfort.tsselect.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArgParseTest {

    @Test
    fun parsesSourceDestinationAndPids() {
        val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "0x1000", "0x1001"))!!

        assertEquals("src.m2t", parsed.src)
        assertEquals("dst.m2t", parsed.dst)
        assertEquals(1, parsed.pidMap[0x1000].toInt())
        assertEquals(1, parsed.pidMap[0x1001].toInt())
        assertEquals(2, parsed.pidMap.count { it.toInt() != 0 })
    }

    @Test
    fun excludeOptionInvertsTheMap() {
        val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "-x", "0x0012", "0x0014"))!!

        assertEquals(0, parsed.pidMap[0x12].toInt())
        assertEquals(0, parsed.pidMap[0x14].toInt())
        assertEquals(1, parsed.pidMap[0x1000].toInt())
    }

    @Test
    fun uppercaseExcludeOptionIsAccepted() {
        val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "-X", "0x0012"))!!

        assertEquals(0, parsed.pidMap[0x12].toInt())
        assertEquals(1, parsed.pidMap[0x13].toInt())
    }

    @Test
    fun outOfRangePidsAreIgnored() {
        val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "0x2000", "0x100"))!!

        assertEquals(1, parsed.pidMap.count { it.toInt() != 0 })
        assertEquals(1, parsed.pidMap[0x100].toInt())
    }

    @Test
    fun invalidOptionIsRejected() {
        assertNull(parseArgs(arrayOf("src.m2t", "dst.m2t", "-q", "0x100")))
    }

    @Test
    fun noPidsSelectsNothing() {
        val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t"))!!

        assertEquals(0, parsed.pidMap.count { it.toInt() != 0 })
    }
}
