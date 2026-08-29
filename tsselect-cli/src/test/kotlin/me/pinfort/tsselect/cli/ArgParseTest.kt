package me.pinfort.tsselect.cli

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ArgParseTest :
    StringSpec({
        "parses source, destination and pids" {
            val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "0x1000", "0x1001"))!!

            parsed.src shouldBe "src.m2t"
            parsed.dst shouldBe "dst.m2t"
            parsed.pidMap[0x1000].toInt() shouldBe 1
            parsed.pidMap[0x1001].toInt() shouldBe 1
            parsed.pidMap.count { it.toInt() != 0 } shouldBe 2
        }

        "exclude option inverts the map" {
            val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "-x", "0x0012", "0x0014"))!!

            parsed.pidMap[0x12].toInt() shouldBe 0
            parsed.pidMap[0x14].toInt() shouldBe 0
            parsed.pidMap[0x1000].toInt() shouldBe 1
        }

        "uppercase exclude option is accepted" {
            val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "-X", "0x0012"))!!

            parsed.pidMap[0x12].toInt() shouldBe 0
            parsed.pidMap[0x13].toInt() shouldBe 1
        }

        "out of range pids are ignored" {
            val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t", "0x2000", "0x100"))!!

            parsed.pidMap.count { it.toInt() != 0 } shouldBe 1
            parsed.pidMap[0x100].toInt() shouldBe 1
        }

        "invalid option is rejected" {
            parseArgs(arrayOf("src.m2t", "dst.m2t", "-q", "0x100")) shouldBe null
        }

        "no pids selects nothing" {
            val parsed = parseArgs(arrayOf("src.m2t", "dst.m2t"))!!

            parsed.pidMap.count { it.toInt() != 0 } shouldBe 0
        }
    })
