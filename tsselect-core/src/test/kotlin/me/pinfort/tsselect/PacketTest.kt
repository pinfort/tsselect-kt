package me.pinfort.tsselect

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PacketTest :
    StringSpec({
        "parses ts header fields" {
            val packet = tsPacket(pid = 0x1abc, cc = 0x0d, afc = 3, tei = true, scrambling = 2)
            val header = TsHeader()
            header.parse(packet, 0)

            header.sync shouldBe 0x47
            header.transportErrorIndicator shouldBe 1
            header.payloadUnitStartIndicator shouldBe 0
            header.transportPriority shouldBe 0
            header.pid shouldBe 0x1abc
            header.transportScramblingControl shouldBe 2
            header.adaptationFieldControl shouldBe 3
            header.continuityCounter shouldBe 0x0d
        }

        "parses discontinuity indicator" {
            val af = AdaptationField()
            af.parse(byteArrayOf(1, 0x80.toByte()), 0)
            af.discontinuityIndicator shouldBe 1
            af.adaptationFieldLength shouldBe 1
        }

        "parses pcr with c faithful bit packing" {
            // length=7, pcr_flag, PCR bytes 00 00 00 01 80 01:
            // base (1 shl 10) or ((0x80 and 0x80) shl 2) or 0x01 = 0x400 or 0x201
            val af = AdaptationField()
            af.parse(byteArrayOf(7, 0x10, 0x00, 0x00, 0x00, 0x01, 0x80.toByte(), 0x01), 0)
            af.pcrFlag shouldBe 1
            af.programClockReference shouldBe 0x601L
        }

        "truncated adaptation field clears struct" {
            // pcr_flag set but only 1 byte of AF: malformed, struct must be zeroed.
            val af = AdaptationField()
            af.parse(byteArrayOf(1, 0x10), 0)
            af.pcrFlag shouldBe 0
            af.adaptationFieldLength shouldBe 0
            af.programClockReference shouldBe 0L
        }

        "zero length adaptation field clears struct" {
            val af = AdaptationField()
            af.discontinuityIndicator = 1
            af.parse(byteArrayOf(0, 0x7f), 0)
            af.discontinuityIndicator shouldBe 0
            af.adaptationFieldLength shouldBe 0
        }
    })
