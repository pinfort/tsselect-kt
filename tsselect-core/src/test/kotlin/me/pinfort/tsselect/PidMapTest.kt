package me.pinfort.tsselect

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PidMapTest :
    StringSpec({
        "selects listed pids" {
            val map = pidMapOf(listOf(0x100, 0x1fff))

            map[0x100].toInt() shouldBe 1
            map[0x1fff].toInt() shouldBe 1
            map[0x101].toInt() shouldBe 0
        }

        "ignores out of range pids" {
            val map = pidMapOf(listOf(-1, 8192, 99999, 0x12))

            map.size shouldBe PID_COUNT
            map[0x12].toInt() shouldBe 1
            map.count { it.toInt() != 0 } shouldBe 1
        }

        "exclude inverts the selection" {
            val map = pidMapOf(listOf(0x12, 0x14), exclude = true)

            map[0x12].toInt() shouldBe 0
            map[0x14].toInt() shouldBe 0
            map[0x100].toInt() shouldBe 1
            map.count { it.toInt() != 0 } shouldBe PID_COUNT - 2
        }

        "exclude nothing selects everything" {
            val map = pidMapOf(emptyList(), exclude = true)

            map.count { it.toInt() != 0 } shouldBe PID_COUNT
        }
    })
