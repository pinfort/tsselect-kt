package me.pinfort.tsselect

/**
 * One progress notification. Fired once per input chunk read by [tsDump] and
 * [tsSelect], then once more with [finished] set to `true` when the run
 * completes. A run that fails (throws) fires no finish event, matching the C
 * tool's `goto LAST`, which skips the finish line.
 *
 * @property chunkIndex 0 for the first chunk, incrementing by one thereafter.
 *   Published so a caller can throttle repaints (e.g. every 16th chunk)
 *   without knowing the library's internal chunk size.
 * @property bytesProcessed how many bytes of the input have been consumed so
 *   far.
 * @property totalBytes the total the caller declared when starting the run;
 *   0 when the size is unknown.
 * @property finished `false` for every per-chunk notification, `true` for the
 *   single notification fired when the run completes successfully.
 */
public data class Progress(
    val chunkIndex: Int,
    val bytesProcessed: Long,
    val totalBytes: Long,
    val finished: Boolean,
) {
    /**
     * Percent times 100, i.e. the C tool's `(int)(10000 * offset / total)`.
     * Integer arithmetic on purpose - floating point would round differently
     * at the boundaries. Yields 0 when [totalBytes] is unknown (`<= 0`, e.g. a
     * FIFO, `/dev/stdin` or a growing capture), where the C tool divides by
     * zero instead. Not clamped: a file that grows during the read
     * legitimately reports over 100%.
     */
    public val basisPoints: Int
        get() = if (totalBytes <= 0L) 0 else (10000L * bytesProcessed / totalBytes).toInt()
}

/**
 * Receives progress notifications from the chunk loops in [tsDump] and
 * [tsSelect]. The library never formats or prints; a caller that wants a
 * progress bar supplies its own listener and does its own throttling (see
 * `StderrProgressListener` in the CLI module for an example).
 */
public fun interface ProgressListener {
    public fun onProgress(progress: Progress)

    public companion object {
        /** A listener that discards every notification. */
        public val NONE: ProgressListener = ProgressListener { }
    }
}
