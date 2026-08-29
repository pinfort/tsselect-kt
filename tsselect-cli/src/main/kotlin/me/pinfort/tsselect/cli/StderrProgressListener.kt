package me.pinfort.tsselect.cli

import me.pinfort.tsselect.Progress
import me.pinfort.tsselect.ProgressListener
import java.util.Locale

// Reproduces the C progress bar: a percentage repainted on every 16th chunk,
// then a "finish" line. The throttle lives here rather than in the library so
// that the library never has to know about terminals; the chunk index it
// throttles on comes from the library, so the repaint rate no longer depends on
// the library's buffer size.
class StderrProgressListener : ProgressListener {
    override fun onProgress(progress: Progress) {
        if (progress.finished) {
            System.err.print("\rprocessing: finish\n")
            return
        }
        if (progress.chunkIndex and 0x0f == 0) {
            val pct = progress.basisPoints
            System.err.print("\rprocessing: %2d.%02d%%".format(Locale.ROOT, pct / 100, pct % 100))
        }
    }
}
