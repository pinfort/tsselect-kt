package me.pinfort.tsselect

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

class TsSelectIntegrationTest :
    StringSpec({
        fun tempFile(name: String): File {
            val dir = File("build/test-tmp")
            dir.mkdirs()
            return File(dir, name)
        }

        "selects single pid and strips 192 byte trailers" {
            val packets = mutableListOf<ByteArray>()
            for (cc in 0 until 10) {
                packets += tsPacket(pid = 0x100, cc = cc, size = 192)
                packets += tsPacket(pid = 0x101, cc = cc, size = 192)
            }
            val src = tempFile("select-src.m2ts")
            src.writeBytes(stream(*packets.toTypedArray()))
            val dst = tempFile("select-dst.ts")

            val result = tsSelect(src, dst, PidSelection.of(listOf(0x100)))

            result.unitSize shouldBe 192
            result.packetsWritten shouldBe 10
            val out = dst.readBytes()
            out.size shouldBe 10 * 188
            val header = TsHeader()
            for (i in 0 until 10) {
                header.parse(out, i * 188)
                header.sync shouldBe 0x47
                header.pid shouldBe 0x100
                header.continuityCounter shouldBe i
            }
        }

        "select all pids round trips to 188 byte stream" {
            val packets = Array(15) { tsPacket(pid = 0x1000, cc = it and 0x0f, size = 204) }
            val src = tempFile("roundtrip-src.ts")
            src.writeBytes(stream(*packets))
            val dst = tempFile("roundtrip-dst.ts")

            val result = tsSelect(src, dst, PidSelection.ALL)

            result.unitSize shouldBe 204
            val out = dst.readBytes()
            out.size shouldBe 15 * 188
            for (i in 0 until 15) {
                (out[i * 188].toInt() and 0xff) shouldBe 0x47
            }
        }

        "selects from streams without touching the filesystem" {
            val packets = mutableListOf<ByteArray>()
            for (cc in 0 until 8) {
                packets += tsPacket(pid = 0x100, cc = cc)
                packets += tsPacket(pid = 0x101, cc = cc)
            }
            val bytes = stream(*packets.toTypedArray())
            val out = ByteArrayOutputStream()

            val result =
                tsSelect(ByteArrayInputStream(bytes), out, bytes.size.toLong(), PidSelection.of(listOf(0x101)))

            val result2 = out.toByteArray()
            result2.size shouldBe 8 * 188
            val header = TsHeader()
            for (i in 0 until 8) {
                header.parse(result2, i * 188)
                header.pid shouldBe 0x101
            }
            result.unitSize shouldBe 188
            result.packetsRead shouldBe 16
            result.packetsWritten shouldBe 8
        }

        "exclude map drops only the listed pids" {
            val packets = mutableListOf<ByteArray>()
            for (cc in 0 until 6) {
                packets += tsPacket(pid = 0x100, cc = cc)
                packets += tsPacket(pid = 0x12, cc = cc)
            }
            val bytes = stream(*packets.toTypedArray())
            val out = ByteArrayOutputStream()

            tsSelect(
                ByteArrayInputStream(bytes),
                out,
                bytes.size.toLong(),
                PidSelection.of(listOf(0x12), exclude = true),
            )

            val result = out.toByteArray()
            result.size shouldBe 6 * 188
            val header = TsHeader()
            for (i in 0 until 6) {
                header.parse(result, i * 188)
                header.pid shouldBe 0x100
            }
        }

        "reports every chunk in order and finishes exactly once" {
            val packets = Array(200) { tsPacket(pid = 0x100, cc = it and 0x0f) }
            val bytes = stream(*packets)
            val seen = mutableListOf<Progress>()

            tsSelect(
                ByteArrayInputStream(bytes),
                ByteArrayOutputStream(),
                bytes.size.toLong(),
                PidSelection.of(listOf(0x100)),
                { seen += it },
            )

            val ongoing = seen.filter { !it.finished }
            ongoing.map { it.chunkIndex } shouldBe ongoing.indices.toList()
            (ongoing.size > 1) shouldBe true
            seen.count { it.finished } shouldBe 1
            seen.last().finished shouldBe true
            (seen.last().bytesProcessed > 0) shouldBe true
        }

        "a format failure reports no finish event" {
            val bytes = ByteArray(4096)
            val seen = mutableListOf<Progress>()

            shouldThrow<TsFormatException> {
                tsSelect(
                    ByteArrayInputStream(bytes),
                    ByteArrayOutputStream(),
                    bytes.size.toLong(),
                    PidSelection.ALL,
                    { seen += it },
                )
            }

            seen.none { it.finished } shouldBe true
        }

        "non ts input throws ts format exception" {
            val bytes = ByteArray(4096)

            shouldThrow<TsFormatException> {
                tsSelect(ByteArrayInputStream(bytes), ByteArrayOutputStream(), bytes.size.toLong(), PidSelection.ALL)
            }
        }

        "a failing write throws a write exception and no finish event" {
            val packets = Array(20) { tsPacket(pid = 0x100, cc = it and 0x0f) }
            val bytes = stream(*packets)
            val seen = mutableListOf<Progress>()
            val failing =
                object : OutputStream() {
                    override fun write(b: Int) = throw IOException("boom")

                    override fun write(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ) = throw IOException("boom")
                }

            val e =
                shouldThrow<TsWriteException> {
                    tsSelect(
                        ByteArrayInputStream(bytes),
                        failing,
                        bytes.size.toLong(),
                        PidSelection.ALL,
                        { seen += it },
                    )
                }

            (e.cause is IOException) shouldBe true
            seen.none { it.finished } shouldBe true
        }

        "a missing source leaves the destination untouched" {
            val src = File("build/test-tmp/select-does-not-exist.ts")
            val dst = tempFile("select-never-created.ts")
            dst.delete()

            val e = shouldThrow<TsSourceOpenException> { tsSelect(src, dst, PidSelection.ALL) }

            e.path shouldBe src.path
            (e.cause is FileNotFoundException) shouldBe true
            dst.exists() shouldBe false
        }

        "an unopenable destination is reported as a destination failure" {
            val src = tempFile("dst-fail-src.ts")
            src.writeBytes(stream(*Array(12) { tsPacket(pid = 0x100, cc = it and 0x0f) }))
            val dst = File("build/test-tmp/no-such-dir/out.ts")

            val e = shouldThrow<TsDestinationOpenException> { tsSelect(src, dst, PidSelection.ALL) }

            e.path shouldBe dst.path
            (e.cause is FileNotFoundException) shouldBe true
        }
    })
