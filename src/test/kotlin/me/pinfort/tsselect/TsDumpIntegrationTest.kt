package me.pinfort.tsselect

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class TsDumpIntegrationTest {

    private fun tempFile(name: String, content: ByteArray): File {
        val dir = File("build/test-tmp")
        dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(content)
        return file
    }

    private fun captureStdout(block: () -> Unit): String {
        val original = System.out
        val bout = ByteArrayOutputStream()
        System.setOut(PrintStream(bout, true, Charsets.UTF_8))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return bout.toString(Charsets.UTF_8)
    }

    @Test
    fun dumpsCleanSinglePidStream() {
        val packets = Array(12) { tsPacket(pid = 0x100, cc = it and 0x0f) }
        val file = tempFile("clean.ts", stream(*packets))

        val out = captureStdout { TsDump().run(file.path) }

        assertEquals("pid=0x0100, total=      12, d=  0, e=  0, scrambling=0, offset=0\n", out)
    }

    @Test
    fun dumpsInterleavedPidsWithOffsets() {
        val packets = mutableListOf<ByteArray>()
        for (cc in 0 until 10) {
            packets += tsPacket(pid = 0x100, cc = cc)
            packets += tsPacket(pid = 0x101, cc = cc)
        }
        val file = tempFile("interleaved.ts", stream(*packets.toTypedArray()))

        val out = captureStdout { TsDump().run(file.path) }

        assertEquals(
            "pid=0x0100, total=      10, d=  0, e=  0, scrambling=0, offset=0\n" +
                "pid=0x0101, total=      10, d=  0, e=  0, scrambling=0, offset=188\n",
            out
        )
    }

    @Test
    fun reportsContinuityGapAsDrop() {
        val ccs = listOf(0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12)
        val packets = ccs.map { tsPacket(pid = 0x100, cc = it) }
        val file = tempFile("gap.ts", stream(*packets.toTypedArray()))

        val out = captureStdout { TsDump().run(file.path) }

        assertEquals("pid=0x0100, total=      12, d=  1, e=  0, scrambling=0, offset=0\n", out)
    }

    @Test
    fun missingFilePrintsNothingToStdout() {
        val out = captureStdout { TsDump().run("build/test-tmp/does-not-exist.ts") }
        assertEquals("", out)
    }
}
