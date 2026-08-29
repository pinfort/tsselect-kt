package me.pinfort.tsselect

// Receives progress notifications from the chunk loops in TsDump and tsSelect.
// The library never formats or prints; a caller that wants a progress bar
// supplies its own listener. onProgress fires once per read chunk - throttling
// and formatting are the caller's business.
interface ProgressListener {
    fun onProgress(
        processed: Long,
        total: Long,
    ) {}

    fun onFinish() {}

    companion object {
        val NONE: ProgressListener = object : ProgressListener {}
    }
}
