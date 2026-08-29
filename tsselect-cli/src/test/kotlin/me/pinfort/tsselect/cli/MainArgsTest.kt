package me.pinfort.tsselect.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class MainArgsTest {

    @Test
    fun parsesHexWithPrefix() {
        assertEquals(0x1000, strtolBase0("0x1000"))
        assertEquals(0x12, strtolBase0("0X12"))
    }

    @Test
    fun parsesOctalWithLeadingZero() {
        assertEquals(8, strtolBase0("010"))
        assertEquals(0, strtolBase0("0"))
    }

    @Test
    fun parsesDecimal() {
        assertEquals(8191, strtolBase0("8191"))
        assertEquals(0, strtolBase0("0x"))
    }

    @Test
    fun invalidInputYieldsZeroLikeStrtol() {
        assertEquals(0, strtolBase0("junk"))
        assertEquals(0, strtolBase0(""))
    }

    @Test
    fun parsesLongestValidPrefix() {
        assertEquals(123, strtolBase0("123abc"))
        assertEquals(0x1f, strtolBase0("0x1fzz"))
    }

    @Test
    fun parsesSignAndWhitespace() {
        assertEquals(-5, strtolBase0(" -5"))
        assertEquals(7, strtolBase0("+7"))
    }
}
