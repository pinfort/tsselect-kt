package me.pinfort.tsselect.cli

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ArgParseTest :
    StringSpec({
        "parses source, destination and pids" {
            val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "0x1000", "0x1001"))!!

            parsed.src shouldBe "src.m2t"
            parsed.dst shouldBe "dst.m2t"
            (0x1000 in parsed.pids) shouldBe true
            (0x1001 in parsed.pids) shouldBe true
            parsed.pids.size shouldBe 2
        }

        "exclude option inverts the map" {
            val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "-x", "0x0012", "0x0014"))!!

            (0x12 in parsed.pids) shouldBe false
            (0x14 in parsed.pids) shouldBe false
            (0x1000 in parsed.pids) shouldBe true
        }

        "uppercase exclude option is accepted" {
            val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "-X", "0x0012"))!!

            (0x12 in parsed.pids) shouldBe false
            (0x13 in parsed.pids) shouldBe true
        }

        "out of range pids are ignored" {
            val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "0x2000", "0x100"))!!

            parsed.pids.size shouldBe 1
            (0x100 in parsed.pids) shouldBe true
        }

        "invalid option is rejected" {
            parseArgs(arrayOf("src.m2t", "dst.m2t", "-q", "0x100")) shouldBe null
        }

        "no pids selects nothing" {
            val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t"))!!

            parsed.pids.size shouldBe 0
        }
    })
