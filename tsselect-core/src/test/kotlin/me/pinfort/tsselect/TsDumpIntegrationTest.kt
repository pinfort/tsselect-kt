package me.pinfort.tsselect

import java.io.File
import java.io.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TsDumpIntegrationTest {
    private fun tempFile(
        name: String,
        content: ByteArray,
    ): File {
        val dir = File("build/test-tmp")
        dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(content)
        return file
    }

    private fun dumpReport(file: File): String {
        val dump = TsDump()
        dump.dump(file)
        return dump.report().format()
    }

    @Test
    fun dumpsCleanSinglePidStream() {
        val packets = Array(12) { tsPacket(pid = 0x100, cc = it and 0x0f) }
        val file = tempFile("clean.ts", stream(*packets))

        assertEquals(
            "pid=0x0100, total=      12, d=  0, e=  0, scrambling=0, offset=0\n",
            dumpReport(file),
        )
    }

    @Test
    fun dumpsInterleavedPidsWithOffsets() {
        val packets = mutableListOf<ByteArray>()
        for (cc in 0 until 10) {
            packets += tsPacket(pid = 0x100, cc = cc)
            packets += tsPacket(pid = 0x101, cc = cc)
        }
        val file = tempFile("interleaved.ts", stream(*packets.toTypedArray()))

        assertEquals(
            "pid=0x0100, total=      10, d=  0, e=  0, scrambling=0, offset=0\n" +
                "pid=0x0101, total=      10, d=  0, e=  0, scrambling=0, offset=188\n",
            dumpReport(file),
        )
    }

    @Test
    fun reportsContinuityGapAsDrop() {
        val ccs = listOf(0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12)
        val packets = ccs.map { tsPacket(pid = 0x100, cc = it) }
        val file = tempFile("gap.ts", stream(*packets.toTypedArray()))

        assertEquals(
            "pid=0x0100, total=      12, d=  1, e=  0, scrambling=0, offset=0\n",
            dumpReport(file),
        )
    }

    @Test
    fun missingFileThrows() {
        assertFailsWith<FileNotFoundException> {
            TsDump().dump(File("build/test-tmp/does-not-exist.ts"))
        }
    }

    @Test
    fun nonTsInputThrowsTsFormatException() {
        val file = tempFile("garbage.bin", ByteArray(4096))

        assertFailsWith<TsFormatException> { TsDump().dump(file) }
    }

    @Test
    fun reportExposesStatsWithoutFormatting() {
        val packets = Array(12) { tsPacket(pid = 0x1fc8, cc = it and 0x0f) }
        val file = tempFile("model.ts", stream(*packets))

        val dump = TsDump()
        dump.dump(file)
        val report = dump.report()

        assertEquals(0, report.resyncCount)
        assertEquals(listOf(PidReport(0x1fc8, 12, 0, 0, 0, 0)), report.pids)
    }
}
