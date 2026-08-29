package me.pinfort.tsselect

// One progress notification. Fired once per input chunk read by tsDump() and
// tsSelect(), then once more with finished=true when the run completes. A run
// that fails fires no finish event, matching the C tool's `goto LAST`, which
// skips the finish line.
public data class Progress(
    // 0 for the first chunk, incrementing by one thereafter. Published so a
    // caller can throttle repaints without knowing the library's chunk size.
    val chunkIndex: Int,
    val bytesProcessed: Long,
    // The total the caller declared; 0 when the size is unknown.
    val totalBytes: Long,
    val finished: Boolean,
) {
    // Percent times 100, i.e. the C tool's (int)(10000 * offset / total).
    // Integer arithmetic on purpose - floating point would round differently at
    // the boundaries. Yields 0 when the total is unknown (a FIFO, /dev/stdin or
    // a growing capture), where the C tool divides by zero. Not clamped: a file
    // that grows during the read legitimately reports over 100%.
    public val basisPoints: Int
        get() = if (totalBytes <= 0L) 0 else (10000L * bytesProcessed / totalBytes).toInt()
}

// Receives progress notifications from the chunk loops in tsDump and tsSelect.
// The library never formats or prints; a caller that wants a progress bar
// supplies its own listener and does its own throttling.
public fun interface ProgressListener {
    public fun onProgress(progress: Progress)

    public companion object {
        public val NONE: ProgressListener = ProgressListener { }
    }
}
