package me.pinfort.tsselect

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        tsSelect(src, dst, pidMap)

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
        tsSelect(src, dst, pidMap)

        val out = dst.readBytes()
        assertEquals(15 * 188, out.size)
        for (i in 0 until 15) {
            assertEquals(0x47, out[i * 188].toInt() and 0xff)
        }
    }

    @Test
    fun selectsFromStreamsWithoutTouchingTheFilesystem() {
        val packets = mutableListOf<ByteArray>()
        for (cc in 0 until 8) {
            packets += tsPacket(pid = 0x100, cc = cc)
            packets += tsPacket(pid = 0x101, cc = cc)
        }
        val bytes = stream(*packets.toTypedArray())
        val out = ByteArrayOutputStream()

        tsSelect(ByteArrayInputStream(bytes), out, bytes.size.toLong(), pidMapOf(listOf(0x101)))

        val result = out.toByteArray()
        assertEquals(8 * 188, result.size)
        val header = TsHeader()
        for (i in 0 until 8) {
            header.parse(result, i * 188)
            assertEquals(0x101, header.pid)
        }
    }

    @Test
    fun excludeMapDropsOnlyTheListedPids() {
        val packets = mutableListOf<ByteArray>()
        for (cc in 0 until 6) {
            packets += tsPacket(pid = 0x100, cc = cc)
            packets += tsPacket(pid = 0x12, cc = cc)
        }
        val bytes = stream(*packets.toTypedArray())
        val out = ByteArrayOutputStream()

        tsSelect(ByteArrayInputStream(bytes), out, bytes.size.toLong(), pidMapOf(listOf(0x12), exclude = true))

        val result = out.toByteArray()
        assertEquals(6 * 188, result.size)
        val header = TsHeader()
        for (i in 0 until 6) {
            header.parse(result, i * 188)
            assertEquals(0x100, header.pid)
        }
    }

    @Test
    fun reportsProgressAndFinishToTheListener() {
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

        assertEquals(true, finished)
        assertEquals(true, lastProcessed > 0)
    }

    @Test
    fun nonTsInputThrowsTsFormatException() {
        val bytes = ByteArray(4096)

        assertFailsWith<TsFormatException> {
            tsSelect(ByteArrayInputStream(bytes), ByteArrayOutputStream(), bytes.size.toLong(), ByteArray(8192))
        }
    }
}
