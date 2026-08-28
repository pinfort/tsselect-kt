package me.pinfort.tsselect

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class TsSelectIntegrationTest {

    private fun tempFile(name: String): File {
        val dir = File("build/test-tmp")
        dir.mkdirs()
        return File(dir, name)
    }

    @Test
    fun selectsSinglePidAndStrips192ByteTrailers() {
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
        tsSelect(src.path, dst.path, pidMap)

        val out = dst.readBytes()
        assertEquals(10 * 188, out.size)
        val header = TsHeader()
        for (i in 0 until 10) {
            header.parse(out, i * 188)
            assertEquals(0x47, header.sync)
            assertEquals(0x100, header.pid)
            assertEquals(i, header.continuityCounter)
        }
    }

    @Test
    fun selectAllPidsRoundTripsTo188ByteStream() {
        val packets = Array(15) { tsPacket(pid = 0x1000, cc = it and 0x0f, size = 204) }
        val src = tempFile("roundtrip-src.ts")
        src.writeBytes(stream(*packets))
        val dst = tempFile("roundtrip-dst.ts")

        val pidMap = ByteArray(8192) { 1 }
        tsSelect(src.path, dst.path, pidMap)

        val out = dst.readBytes()
        assertEquals(15 * 188, out.size)
        for (i in 0 until 15) {
            assertEquals(0x47, out[i * 188].toInt() and 0xff)
        }
    }
}
