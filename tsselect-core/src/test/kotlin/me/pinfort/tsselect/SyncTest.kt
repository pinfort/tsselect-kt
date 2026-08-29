package me.pinfort.tsselect

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SyncTest :
    StringSpec({
        fun packets(
            count: Int,
            size: Int,
        ): ByteArray = stream(*Array(count) { tsPacket(pid = 0x100, cc = it and 0x0f, size = size) })

        "selects unit size 188" {
            val buf = packets(10, 188)
            selectUnitSize(buf, buf.size) shouldBe 188
        }

        "selects unit size 192" {
            val buf = packets(10, 192)
            selectUnitSize(buf, buf.size) shouldBe 192
        }

        "selects unit size 204" {
            val buf = packets(10, 204)
            selectUnitSize(buf, buf.size) shouldBe 204
        }

        "rejects too few packets" {
            // 8 packets yield only 7 counted intervals; the m >= 8 check fails.
            val buf = packets(8, 188)
            selectUnitSize(buf, buf.size) shouldBe 0
        }

        "rejects garbage" {
            // no sync bytes at all: the histogram stays empty
            val buf = ByteArray(4096) { 0x55 }
            selectUnitSize(buf, buf.size) shouldBe 0
        }

        "resync finds sync after garbage" {
            val garbage = ByteArray(100) { 0x11 }
            val buf = stream(garbage, packets(10, 188))
            resync(buf, 0, buf.size, 188) shouldBe 100
        }

        "resync needs eight consecutive syncs" {
            val garbage = ByteArray(50) { 0x11 }
            // Only 7 packets after the garbage: never 8 syncs in a row.
            val buf = stream(garbage, packets(7, 188))
            resync(buf, 0, buf.size, 188) shouldBe -1
        }

        "resync force finds short tail" {
            val garbage = ByteArray(10) { 0x11 }
            val buf = stream(garbage, packets(2, 188))
            resyncForce(buf, 0, buf.size, 188) shouldBe 10
        }

        "resync force gives up without sync" {
            val buf = ByteArray(400) { 0x11 }
            resyncForce(buf, 0, buf.size, 188) shouldBe -1
        }
    })
