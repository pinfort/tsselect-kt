package me.pinfort.tsselect

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ProgressTest :
    StringSpec({
        "basis points are the C tool's integer percentage times 100" {
            Progress(0, 1234, 10000, finished = false).basisPoints shouldBe 1234
            Progress(0, 1, 3, finished = false).basisPoints shouldBe 3333
            Progress(0, 10000, 10000, finished = false).basisPoints shouldBe 10000
        }

        "an unknown total reads as zero instead of dividing by zero" {
            Progress(0, 8192, 0, finished = false).basisPoints shouldBe 0
            Progress(0, 8192, -1, finished = false).basisPoints shouldBe 0
        }

        "a growing input is not clamped to 100 percent" {
            Progress(0, 200, 100, finished = false).basisPoints shouldBe 20000
        }

        "no progress yet is zero" {
            Progress(0, 0, 1000, finished = false).basisPoints shouldBe 0
        }
    })
