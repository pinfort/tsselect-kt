package me.pinfort.tsselect

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ReportFormatTest :
    StringSpec({
        "formats pid lines only when there are no resyncs" {
            val report =
                TsDumpReport(
                    resyncCount = 0,
                    resyncEntries = emptyList(),
                    pids =
                        listOf(
                            PidReport(0x100, 12, 1, 2, 3, 0),
                            PidReport(0x1fff, 4, 0, 0, 0, 188),
                        ),
                )

            report.format() shouldBe
                "pid=0x0100, total=      12, d=  1, e=  2, scrambling=3, offset=0\n" +
                "pid=0x1fff, total=       4, d=  0, e=  0, scrambling=0, offset=188\n"
        }

        "formats resync header and drops before pid lines" {
            val report =
                TsDumpReport(
                    resyncCount = 2,
                    resyncEntries =
                        listOf(
                            ResyncEntry(0xf4224, 0xf42ed, 1, listOf(DropEntry(0x100f, 0xf42ed))),
                            ResyncEntry(0x2dc679, 0x2dc73a, 0, emptyList()),
                        ),
                    pids = listOf(PidReport(0x100f, 7, 2, 0, 0, 0)),
                )

            report.format() shouldBe
                "total sync error: 2\n" +
                "  resync[0] : miss=0x0000000f4224, sync=0x0000000f42ed, drop=1\n" +
                "    drop[0] : pid=0x100f, pos=0x0000000f42ed\n" +
                "  resync[1] : miss=0x0000002dc679, sync=0x0000002dc73a, drop=0\n" +
                "pid=0x100f, total=       7, d=  2, e=  0, scrambling=0, offset=0\n"
        }

        "empty report formats to empty string" {
            TsDumpReport(0, emptyList(), emptyList()).format() shouldBe ""
        }
    })
