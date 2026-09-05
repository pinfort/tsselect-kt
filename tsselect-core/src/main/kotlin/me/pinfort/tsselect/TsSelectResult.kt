package me.pinfort.tsselect

/**
 * What a [tsSelect] run did.
 *
 * @property unitSize the detected packet grid: 188 (TS), 192 (M2TS) or 204
 *   (Reed-Solomon).
 * @property packetsRead packets seen in the source, regardless of selection.
 * @property packetsWritten packets written to the destination, i.e. those
 *   whose PID matched the [PidSelection]. Together with [packetsRead], lets
 *   the caller tell an empty selection from an empty input without
 *   re-reading the output.
 */
public data class TsSelectResult(
    val unitSize: Int,
    val packetsRead: Long,
    val packetsWritten: Long,
)
