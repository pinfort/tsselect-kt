package me.pinfort.tsselect.cli

import me.pinfort.tsselect.ProgressListener
import java.util.Locale

// Reproduces the C progress bar: a percentage repainted on every 16th chunk,
// then a "finish" line. The throttle lives here rather than in the library so
// that the library never has to know about terminals.
class StderrProgressListener : ProgressListener {
    private var idx = 0

    override fun onProgress(processed: Long, total: Long) {
        if (idx and 0x0f == 0) {
            val pct = (10000L * processed / total).toInt()
            System.err.print("\rprocessing: %2d.%02d%%".format(Locale.ROOT, pct / 100, pct % 100))
        }
        idx += 1
    }

    override fun onFinish() {
        System.err.print("\rprocessing: finish\n")
    }
}
