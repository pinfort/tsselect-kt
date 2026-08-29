package me.pinfort.tsselect

// What a remux run did. unitSize is the detected packet grid (188, 192 or 204);
// the counters are packets seen and packets written, so the caller can tell an
// empty selection from an empty input without re-reading the output.
public data class TsSelectResult(
    val unitSize: Int,
    val packetsRead: Long,
    val packetsWritten: Long,
)
