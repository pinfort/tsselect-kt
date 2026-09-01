package me.pinfort.tsselect

// An immutable set of the PIDs a remux keeps. Replaces the raw ByteArray(8192)
// that used to cross the API boundary unvalidated: the representation is
// private, so a caller cannot hand tsSelect() a short or mutated map.
public class PidSelection private constructor(
    private val map: ByteArray,
) {
    // How many of the 8192 PIDs are selected.
    public val size: Int = map.count { it.toInt() != 0 }

    // Bounds-checked so an out-of-range PID reads as unselected rather than
    // throwing from inside the per-packet loop.
    public operator fun contains(pid: Int): Boolean = pid in 0..<PID_COUNT && map[pid].toInt() != 0

    override fun equals(other: Any?): Boolean = this === other || (other is PidSelection && map.contentEquals(other.map))

    override fun hashCode(): Int = map.contentHashCode()

    override fun toString(): String = "PidSelection(size=$size)"

    public companion object {
        public const val PID_COUNT: Int = 8192

        // Selects no PID at all: a remux with this writes an empty file.
        public val NONE: PidSelection = of(emptyList())

        // Selects every PID: a remux with this rewrites the whole stream as 188.
        public val ALL: PidSelection = of(emptyList(), exclude = true)

        // PIDs outside 0..8191 are silently ignored, as the C argument loop
        // does; exclude=true inverts the map so the listed PIDs are dropped.
        public fun of(
            pids: Collection<Int>,
            exclude: Boolean = false,
        ): PidSelection {
            val map = ByteArray(PID_COUNT)
            for (pid in pids) {
                if (pid in 0 until PID_COUNT) {
                    map[pid] = 1
                }
            }

            if (exclude) {
                for (i in 0 until PID_COUNT) {
                    map[i] = if (map[i].toInt() == 0) 1 else 0
                }
            }

            return PidSelection(map)
        }

        // Parses PID arguments written the way the C tool accepts them, i.e.
        // strtol(s, NULL, 0): 0x/0X hex, leading-zero octal, otherwise decimal.
        // A token that does not parse yields 0 and a token outside 0..8191 is
        // dropped - both exactly as the C argv loop behaves.
        public fun parse(
            tokens: Collection<String>,
            exclude: Boolean = false,
        ): PidSelection = of(tokens.map { strtolBase0(it) }, exclude)
    }
}
