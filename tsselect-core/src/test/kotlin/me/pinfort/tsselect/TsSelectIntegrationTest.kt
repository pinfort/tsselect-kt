package me.pinfort.tsselect

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

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

            val pidMap = ByteArray(8192)
            pidMap[0x100] = 1
            tsSelect(src, dst, pidMap)

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

            val pidMap = ByteArray(8192) { 1 }
            tsSelect(src, dst, pidMap)

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

            tsSelect(ByteArrayInputStream(bytes), out, bytes.size.toLong(), pidMapOf(listOf(0x101)))

            val result = out.toByteArray()
            result.size shouldBe 8 * 188
            val header = TsHeader()
            for (i in 0 until 8) {
                header.parse(result, i * 188)
                header.pid shouldBe 0x101
            }
        }

        "exclude map drops only the listed pids" {
            val packets = mutableListOf<ByteArray>()
            for (cc in 0 until 6) {
                packets += tsPacket(pid = 0x100, cc = cc)
                packets += tsPacket(pid = 0x12, cc = cc)
            }
            val bytes = stream(*packets.toTypedArray())
            val out = ByteArrayOutputStream()

            tsSelect(ByteArrayInputStream(bytes), out, bytes.size.toLong(), pidMapOf(listOf(0x12), exclude = true))

            val result = out.toByteArray()
            result.size shouldBe 6 * 188
            val header = TsHeader()
            for (i in 0 until 6) {
                header.parse(result, i * 188)
                header.pid shouldBe 0x100
            }
        }

        "reports progress and finish to the listener" {
            val packets = Array(40) { tsPacket(pid = 0x100, cc = it and 0x0f) }
            val bytes = stream(*packets)
            var finished = false
            var lastProcessed = -1L
            val listener =
                object : ProgressListener {
                    override fun onProgress(
                        processed: Long,
                        total: Long,
                    ) {
                        lastProcessed = processed
                    }

                    override fun onFinish() {
                        finished = true
                    }
                }

            tsSelect(
                ByteArrayInputStream(bytes),
                ByteArrayOutputStream(),
                bytes.size.toLong(),
                pidMapOf(listOf(0x100)),
                listener,
            )

            finished shouldBe true
            (lastProcessed > 0) shouldBe true
        }

        "non ts input throws ts format exception" {
            val bytes = ByteArray(4096)

            shouldThrow<TsFormatException> {
                tsSelect(ByteArrayInputStream(bytes), ByteArrayOutputStream(), bytes.size.toLong(), ByteArray(8192))
            }
        }
    })
