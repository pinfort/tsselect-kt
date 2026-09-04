package me.pinfort.tsselect

import java.util.Locale

/**
 * One packet dropped after a resync, attributed to the [ResyncEntry] whose
 * drop window it fell in (see `TsDumpEngine.addDropInfo`). At most 4 of these
 * are kept per [ResyncEntry]; [ResyncEntry.dropCount] holds the true total.
 *
 * @property pid the PID of the dropped packet.
 * @property pos the byte offset of the dropped packet in the source stream.
 */
public data class DropEntry(
    val pid: Int,
    val pos: Long,
)

/**
 * One loss of packet alignment and its recovery. At most
 * [me.pinfort.tsselect.RESYNC_LOG_MAX] (8) of these are kept per report;
 * [TsDumpReport.resyncCount] holds the true total.
 *
 * A `miss=0` entry at index 0 only means the capture did not start on a
 * packet boundary - it does not by itself indicate lost data.
 *
 * @property miss the byte offset where alignment was lost.
 * @property sync the byte offset where alignment was recovered.
 * @property dropCount how many packets were dropped before recovery; may
 *   exceed `drops.size`, which is capped at 4.
 * @property drops up to the first 4 dropped packets in this window.
 */
public data class ResyncEntry(
    val miss: Long,
    val sync: Long,
    val dropCount: Long,
    val drops: List<DropEntry>,
)

/**
 * Per-PID statistics for one PID that appeared at least once in the stream.
 *
 * @property pid the PID this report covers.
 * @property total packets seen on this PID.
 * @property drop packets where a continuity-counter error was detected.
 * @property error packets with the transport error indicator set, i.e. error
 *   correction failed inside the tuner.
 * @property scrambling packets with the transport scrambling control set,
 *   i.e. encrypted packets.
 * @property firstOffset the byte offset where this PID first appeared.
 */
public data class PidReport(
    val pid: Int,
    val total: Long,
    val drop: Long,
    val error: Long,
    val scrambling: Long,
    val firstOffset: Long,
)

/**
 * The full result of [tsDump]: every resync event (capped, see
 * [ResyncEntry]) and one [PidReport] per PID that appeared in the stream.
 *
 * @property resyncCount the true count of resync events, which may exceed
 *   `resyncEntries.size`.
 * @property resyncEntries up to the first
 *   [me.pinfort.tsselect.RESYNC_LOG_MAX] (8) resync events, in order.
 * @property pids one entry per PID seen, in PID order.
 */
public data class TsDumpReport(
    val resyncCount: Int,
    val resyncEntries: List<ResyncEntry>,
    val pids: List<PidReport>,
)

/**
 * Renders this report exactly as the C `tsselect` prints it, e.g.:
 * ```
 * total sync error: 3
 *   resync[0] : miss=0x000000000000, sync=0x000000000059, drop=0
 *   resync[1] : miss=0x000053be0745, sync=0x000053be0827, drop=11
 *     drop[0] : pid=0x1008, pos=0x000053be0827
 * pid=0x0012, total=  128217, d=  2, e=  0, scrambling=0, offset=8388748
 * ```
 * Returns the text instead of writing it, so the library stays free of any
 * output stream; callers print or log the result themselves.
 */
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
