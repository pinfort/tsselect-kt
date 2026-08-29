package me.pinfort.tsselect

import java.util.Locale

public data class DropEntry(
    val pid: Int,
    val pos: Long,
)

public data class ResyncEntry(
    val miss: Long,
    val sync: Long,
    val dropCount: Long,
    val drops: List<DropEntry>,
)

public data class PidReport(
    val pid: Int,
    val total: Long,
    val drop: Long,
    val error: Long,
    val scrambling: Long,
    val firstOffset: Long,
)

public data class TsDumpReport(
    val resyncCount: Int,
    val resyncEntries: List<ResyncEntry>,
    val pids: List<PidReport>,
)

// Renders the report exactly as the C tsselect prints it. Returns the text
// instead of writing it, so the library stays free of any output stream.
public fun TsDumpReport.format(): String {
    val sb = StringBuilder()

    if (resyncCount > 0) {
        sb.append("total sync error: %d\n".format(Locale.ROOT, resyncCount))
        for ((i, entry) in resyncEntries.withIndex()) {
            sb.append(
                "  resync[%d] : miss=0x%012x, sync=0x%012x, drop=%d\n".format(
                    Locale.ROOT,
                    i,
                    entry.miss,
                    entry.sync,
                    entry.dropCount,
                ),
            )
            for ((j, drop) in entry.drops.withIndex()) {
                sb.append(
                    "    drop[%d] : pid=0x%04x, pos=0x%012x\n".format(
                        Locale.ROOT,
                        j,
                        drop.pid,
                        drop.pos,
                    ),
                )
            }
        }
    }

    for (pid in pids) {
        sb.append(
            "pid=0x%04x, total=%8d, d=%3d, e=%3d, scrambling=%d, offset=%d\n".format(
                Locale.ROOT,
                pid.pid,
                pid.total,
                pid.drop,
                pid.error,
                pid.scrambling,
                pid.firstOffset,
            ),
        )
    }

    return sb.toString()
}
