package me.pinfort.tsselect

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.pinfort.tsselect.PidSelection.Companion.PID_COUNT

class PidSelectionTest :
    StringSpec({
        "selects listed pids" {
            val pids = PidSelection.of(listOf(0x100, 0x1fff))

            (0x100 in pids) shouldBe true
            (0x1fff in pids) shouldBe true
            (0x101 in pids) shouldBe false
        }

        "ignores out of range pids" {
            val pids = PidSelection.of(listOf(-1, 8192, 99999, 0x12))

            (0x12 in pids) shouldBe true
            pids.size shouldBe 1
        }

        "out of range lookups read as unselected" {
            val pids = PidSelection.ALL

            (-1 in pids) shouldBe false
            (PID_COUNT in pids) shouldBe false
        }

        "exclude inverts the selection" {
            val pids = PidSelection.of(listOf(0x12, 0x14), exclude = true)

            (0x12 in pids) shouldBe false
            (0x14 in pids) shouldBe false
            (0x100 in pids) shouldBe true
            pids.size shouldBe PID_COUNT - 2
        }

        "exclude nothing selects everything" {
            PidSelection.of(emptyList(), exclude = true).size shouldBe PID_COUNT
        }

        "NONE and ALL are the empty and full selections" {
            PidSelection.NONE.size shouldBe 0
            PidSelection.ALL.size shouldBe PID_COUNT
            (0x100 in PidSelection.NONE) shouldBe false
            (0x100 in PidSelection.ALL) shouldBe true
        }

        "parses pid tokens in strtol base 0 notation" {
            val pids = PidSelection.parse(listOf("0x100", "0400", "512"))

            (0x100 in pids) shouldBe true
            (0x100 in pids) shouldBe true
            (512 in pids) shouldBe true
            pids.size shouldBe 2
        }

        "unparsable tokens select pid 0 as C strtol does" {
            val pids = PidSelection.parse(listOf("junk"))

            (0 in pids) shouldBe true
            pids.size shouldBe 1
        }

        "parse drops out of range tokens" {
            val pids = PidSelection.parse(listOf("0x2000", "0x100"))

            (0x100 in pids) shouldBe true
            pids.size shouldBe 1
        }

        "parse honours exclude" {
            val pids = PidSelection.parse(listOf("0x12"), exclude = true)

            (0x12 in pids) shouldBe false
            pids.size shouldBe PID_COUNT - 1
        }

        "equality is by content" {
            PidSelection.of(listOf(0x100)) shouldBe PidSelection.of(listOf(0x100))
            PidSelection.of(listOf(0x100)).hashCode() shouldBe PidSelection.of(listOf(0x100)).hashCode()
            (PidSelection.of(listOf(0x100)) == PidSelection.of(listOf(0x101))) shouldBe false
        }
    })
