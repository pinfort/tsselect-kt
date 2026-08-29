package me.pinfort.tsselect

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.FileNotFoundException

class TsDumpIntegrationTest :
    StringSpec({
        fun tempFile(
            name: String,
            content: ByteArray,
        ): File {
            val dir = File("build/test-tmp")
            dir.mkdirs()
            val file = File(dir, name)
            file.writeBytes(content)
            return file
        }

        fun dumpReport(file: File): String = tsDump(file).format()

        "dumps clean single pid stream" {
            val packets = Array(12) { tsPacket(pid = 0x100, cc = it and 0x0f) }
            val file = tempFile("clean.ts", stream(*packets))

            dumpReport(file) shouldBe
                "pid=0x0100, total=      12, d=  0, e=  0, scrambling=0, offset=0\n"
        }

        "dumps interleaved pids with offsets" {
            val packets = mutableListOf<ByteArray>()
            for (cc in 0 until 10) {
                packets += tsPacket(pid = 0x100, cc = cc)
                packets += tsPacket(pid = 0x101, cc = cc)
            }
            val file = tempFile("interleaved.ts", stream(*packets.toTypedArray()))

            dumpReport(file) shouldBe
                "pid=0x0100, total=      10, d=  0, e=  0, scrambling=0, offset=0\n" +
                "pid=0x0101, total=      10, d=  0, e=  0, scrambling=0, offset=188\n"
        }

        "reports continuity gap as drop" {
            val ccs = listOf(0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12)
            val packets = ccs.map { tsPacket(pid = 0x100, cc = it) }
            val file = tempFile("gap.ts", stream(*packets.toTypedArray()))

            dumpReport(file) shouldBe
                "pid=0x0100, total=      12, d=  1, e=  0, scrambling=0, offset=0\n"
        }

        "missing file throws a source open exception carrying the path" {
            val path = "build/test-tmp/does-not-exist.ts"

            val e = shouldThrow<TsSourceOpenException> { tsDump(File(path)) }

            e.path shouldBe path
            (e.cause is FileNotFoundException) shouldBe true
        }

        "non ts input throws ts format exception" {
            val file = tempFile("garbage.bin", ByteArray(4096))

            shouldThrow<TsFormatException> { tsDump(file) }
        }

        "report exposes stats without formatting" {
            val packets = Array(12) { tsPacket(pid = 0x1fc8, cc = it and 0x0f) }
            val file = tempFile("model.ts", stream(*packets))

            val report = tsDump(file)

            report.resyncCount shouldBe 0
            report.pids shouldBe listOf(PidReport(0x1fc8, 12, 0, 0, 0, 0))
        }
    })
