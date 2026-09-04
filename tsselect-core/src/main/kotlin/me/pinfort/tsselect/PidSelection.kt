package me.pinfort.tsselect

/**
 * An immutable set of the PIDs a remux keeps (or drops).
 *
 * Replaces the raw `ByteArray(8192)` that used to cross the API boundary
 * unvalidated: the backing map is private and only ever built through [of] or
 * [parse], so a caller cannot hand [tsSelect] a short or mutated map.
 * Construct one with [of] (parsed PIDs) or [parse] (argv-shaped PID tokens),
 * or use the [NONE] / [ALL] constants.
 */
public class PidSelection private constructor(
    private val map: ByteArray,
) {
    /** How many of the 8192 PIDs are selected. */
    public val size: Int = map.count { it.toInt() != 0 }

    /**
     * Whether [pid] is selected. Bounds-checked so an out-of-range PID reads
     * as unselected rather than throwing from inside the per-packet loop.
     */
    public operator fun contains(pid: Int): Boolean = pid in 0..<PID_COUNT && map[pid].toInt() != 0

    override fun equals(other: Any?): Boolean = this === other || (other is PidSelection && map.contentEquals(other.map))

    override fun hashCode(): Int = map.contentHashCode()

    override fun toString(): String = "PidSelection(size=$size)"

    public companion object {
        /** The number of valid PIDs in an MPEG-2 TS packet header (0..8191). */
        public const val PID_COUNT: Int = 8192

        /** Selects no PID at all: a remux with this writes an empty file. */
        public val NONE: PidSelection = of(emptyList())

        /** Selects every PID: a remux with this rewrites the whole stream as 188. */
        public val ALL: PidSelection = of(emptyList(), exclude = true)

        /**
         * Builds a selection from already-parsed PID numbers.
         *
         * @param pids the PIDs to select; any value outside `0..8191` is
         *   silently ignored, mirroring the C argument loop.
         * @param exclude when true, inverts the map so every PID *except*
         *   those in [pids] is selected.
         */
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

        /**
         * Parses PID arguments written the way the C tool accepts them, via
         * [strtolBase0] (C's `strtol(s, NULL, 0)`): a `0x`/`0X` prefix selects
         * hex, a leading `0` selects octal, otherwise decimal.
         *
         * @param tokens the argv-shaped PID strings. A token that does not
         *   parse yields 0, and a value outside `0..8191` is dropped - both
         *   exactly as the C argv loop behaves.
         * @param exclude when true, inverts the map so every PID *except*
         *   those named in [tokens] is selected.
         */
        public fun parse(
            tokens: Collection<String>,
            exclude: Boolean = false,
        ): PidSelection = of(tokens.map { strtolBase0(it) }, exclude)
    }
}
