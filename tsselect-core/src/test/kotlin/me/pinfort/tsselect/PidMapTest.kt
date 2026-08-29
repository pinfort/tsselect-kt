package me.pinfort.tsselect

import kotlin.test.Test
import kotlin.test.assertEquals

class PidMapTest {
    @Test
    fun selectsListedPids() {
        val map = pidMapOf(listOf(0x100, 0x1fff))

        assertEquals(1, map[0x100].toInt())
        assertEquals(1, map[0x1fff].toInt())
        assertEquals(0, map[0x101].toInt())
    }

    @Test
    fun ignoresOutOfRangePids() {
        val map = pidMapOf(listOf(-1, 8192, 99999, 0x12))

        assertEquals(PID_COUNT, map.size)
        assertEquals(1, map[0x12].toInt())
        assertEquals(1, map.count { it.toInt() != 0 })
    }

    @Test
    fun excludeInvertsTheSelection() {
        val map = pidMapOf(listOf(0x12, 0x14), exclude = true)

        assertEquals(0, map[0x12].toInt())
        assertEquals(0, map[0x14].toInt())
        assertEquals(1, map[0x100].toInt())
        assertEquals(PID_COUNT - 2, map.count { it.toInt() != 0 })
    }

    @Test
    fun excludeNothingSelectsEverything() {
        val map = pidMapOf(emptyList(), exclude = true)

        assertEquals(PID_COUNT, map.count { it.toInt() != 0 })
    }
}
