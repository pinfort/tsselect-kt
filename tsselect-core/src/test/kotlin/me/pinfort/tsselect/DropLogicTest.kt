package me.pinfort.tsselect

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DropLogicTest :
    StringSpec({
        val pid = 0x100

        fun feed(
            dump: TsDump,
            vararg packets: ByteArray,
        ) {
            packets.forEachIndexed { i, p ->
                dump.processPacket(p, 0, 188L * i)
            }
        }

        "normal sequence has no drops" {
            val dump = TsDump()
            feed(dump, tsPacket(pid, 0), tsPacket(pid, 1), tsPacket(pid, 2))
            dump.stats[pid].drop shouldBe 0L
            dump.stats[pid].total shouldBe 3L
        }

        "continuity gap counts one drop" {
            val dump = TsDump()
            feed(dump, tsPacket(pid, 0), tsPacket(pid, 2))
            dump.stats[pid].drop shouldBe 1L
        }

        "continuity wraps from 15 to zero" {
            val dump = TsDump()
            feed(dump, tsPacket(pid, 15), tsPacket(pid, 0))
            dump.stats[pid].drop shouldBe 0L
        }

        "single identical duplicate is not a drop" {
            val dump = TsDump()
            val p = tsPacket(pid, 0)
            feed(dump, p, p)
            dump.stats[pid].drop shouldBe 0L
            dump.stats[pid].duplicateCount shouldBe 1
        }

        "second identical duplicate counts one drop" {
            val dump = TsDump()
            val p = tsPacket(pid, 0)
            feed(dump, p, p, p)
            dump.stats[pid].drop shouldBe 1L
            dump.stats[pid].duplicateCount shouldBe 2
        }

        "same cc different payload counts one drop" {
            val dump = TsDump()
            feed(dump, tsPacket(pid, 0, payloadFill = 0), tsPacket(pid, 0, payloadFill = 1))
            dump.stats[pid].drop shouldBe 1L
        }

        "duplicate count resets when cc advances" {
            val dump = TsDump()
            val p = tsPacket(pid, 0)
            feed(dump, p, p, tsPacket(pid, 1), tsPacket(pid, 1))
            dump.stats[pid].drop shouldBe 0L
            dump.stats[pid].duplicateCount shouldBe 1
        }

        "discontinuity indicator suppresses drop check" {
            val dump = TsDump()
            feed(
                dump,
                tsPacket(pid, 0),
                tsPacket(pid, 5, afc = 3, adaptation = byteArrayOf(1, 0x80.toByte())),
            )
            dump.stats[pid].drop shouldBe 0L
        }

        "null packet pid never drops" {
            val dump = TsDump()
            feed(dump, tsPacket(0x1fff, 0), tsPacket(0x1fff, 7))
            dump.stats[0x1fff].drop shouldBe 0L
            dump.stats[0x1fff].total shouldBe 2L
        }

        "no payload continuity change is a drop" {
            val dump = TsDump()
            feed(
                dump,
                tsPacket(pid, 0, afc = 2, adaptation = byteArrayOf(1, 0)),
                tsPacket(pid, 1, afc = 2, adaptation = byteArrayOf(1, 0)),
            )
            dump.stats[pid].drop shouldBe 1L
        }

        "no payload same continuity is not a drop" {
            val dump = TsDump()
            feed(
                dump,
                tsPacket(pid, 0, afc = 2, adaptation = byteArrayOf(1, 0)),
                tsPacket(pid, 0, afc = 2, adaptation = byteArrayOf(1, 0)),
            )
            dump.stats[pid].drop shouldBe 0L
        }

        "error and scrambling counters accumulate" {
            val dump = TsDump()
            feed(
                dump,
                tsPacket(pid, 0, tei = true, scrambling = 2),
                tsPacket(pid, 1, tei = true),
                tsPacket(pid, 2),
            )
            dump.stats[pid].error shouldBe 2L
            dump.stats[pid].scrambling shouldBe 1L
            dump.stats[pid].drop shouldBe 0L
        }
    })
