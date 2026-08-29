package me.pinfort.tsselect.cli

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.pinfort.tsselect.Progress
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class StderrProgressListenerTest :
    StringSpec({
        fun capture(block: (StderrProgressListener) -> Unit): String {
            val buffer = ByteArrayOutputStream()
            val original = System.err
            System.setErr(PrintStream(buffer, true, Charsets.UTF_8))
            try {
                block(StderrProgressListener())
            } finally {
                System.setErr(original)
            }
            return buffer.toString(Charsets.UTF_8)
        }

        "paints the percentage in the C format" {
            capture { it.onProgress(Progress(0, 1234, 10000, finished = false)) } shouldBe
                "\rprocessing: 12.34%"
        }

        "pads the whole part to two columns as C does" {
            capture { it.onProgress(Progress(0, 105, 10000, finished = false)) } shouldBe
                "\rprocessing:  1.05%"
        }

        "repaints only every sixteenth chunk" {
            val out =
                capture { listener ->
                    for (i in 0..32) {
                        listener.onProgress(Progress(i, 5000, 10000, finished = false))
                    }
                }

            out shouldBe "\rprocessing: 50.00%".repeat(3)
        }

        "an unknown total paints zero instead of dividing by zero" {
            capture { it.onProgress(Progress(0, 8192, 0, finished = false)) } shouldBe
                "\rprocessing:  0.00%"
        }

        "the finish event paints the finish line and nothing else" {
            capture { it.onProgress(Progress(7, 10000, 10000, finished = true)) } shouldBe
                "\rprocessing: finish\n"
        }
    })
