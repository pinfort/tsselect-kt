package me.pinfort.tsselect

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PacketTest :
    StringSpec({
        // Adaptation-field fixtures are written as hex so each sub-field can sit
        // on its own annotated line, which is how the layout is specified.
        fun bytes(hex: String): ByteArray {
            val digits = hex.filterNot { it.isWhitespace() }
            return ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }

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

        "parses every optional sub-field including the extension" {
            // All five flags set, so PCR / OPCR / splice_countdown /
            // transport_private_data / adaptation_field_extension follow back to
            // back - this pins the whole optional chain and the running bounds
            // check that walks it. The extension in turn carries all three of
            // ltw, piecewise_rate and seamless_splice.
            val af = AdaptationField()
            af.parse(
                bytes(
                    "1d" + // adaptation_field_length = 29
                        "1f" + // flags: pcr, opcr, splicing_point, private_data, extension
                        "000000018001" + // PCR: base 1, low bits 0x80 / 0x01
                        "000000020003" + // OPCR: base 2, low bits 0x00 / 0x03
                        "05" + // splice_countdown
                        "02aabb" + // transport_private_data: length 2, then 2 bytes
                        "0b" + // adaptation_field_extension_length = 11
                        "e0" + // extension flags: ltw, piecewise_rate, seamless_splice
                        "8123" + // ltw: valid, offset 0x123
                        "7abcde" + // piecewise_rate 0x3abcde
                        "3f02070409", // seamless_splice: type 3, then DTS_next_AU
                ),
                0,
            )

            af.adaptationFieldLength shouldBe 29
            af.programClockReference shouldBe 0x601L
            af.originalProgramClockReference shouldBe 0x803L
            af.spliceCountdown shouldBe 0x05
            af.transportPrivateDataLength shouldBe 2
            af.adaptationFieldExtensionLength shouldBe 11
            af.ltwFlag shouldBe 1
            af.ltwValidFlag shouldBe 1
            af.ltwOffset shouldBe 0x0123
            af.piecewiseRateFlag shouldBe 1
            af.piecewiseRate shouldBe 0x3abcde
            af.seamlessSpliceFlag shouldBe 1
            af.spliceType shouldBe 3
            af.dtsNextAu shouldBe 0x1c0818204L
        }

        "truncated adaptation field extension clears struct" {
            // The extension claims 11 bytes but only its flags byte is present:
            // malformed, so the whole struct is zeroed.
            val af = AdaptationField()
            af.parse(bytes("03010be0"), 0)
            af.adaptationFieldLength shouldBe 0
            af.adaptationFieldExtensionFlag shouldBe 0
            af.ltwFlag shouldBe 0
        }

        "zero length adaptation field clears struct" {
            val af = AdaptationField()
            af.discontinuityIndicator = 1
            af.parse(byteArrayOf(0, 0x7f), 0)
            af.discontinuityIndicator shouldBe 0
            af.adaptationFieldLength shouldBe 0
        }
    })
