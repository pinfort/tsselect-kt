package me.pinfort.tsselect

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class StrtolTest :
    StringSpec({
        "parses hex with prefix" {
            strtolBase0("0x1000") shouldBe 0x1000
            strtolBase0("0X12") shouldBe 0x12
        }

        "parses octal with leading zero" {
            strtolBase0("010") shouldBe 8
            strtolBase0("0") shouldBe 0
        }

        "parses decimal" {
            strtolBase0("8191") shouldBe 8191
            strtolBase0("0x") shouldBe 0
        }

        "invalid input yields zero like strtol" {
            strtolBase0("junk") shouldBe 0
            strtolBase0("") shouldBe 0
        }

        "parses longest valid prefix" {
            strtolBase0("123abc") shouldBe 123
            strtolBase0("0x1fzz") shouldBe 0x1f
        }

        "parses sign and whitespace" {
            strtolBase0(" -5") shouldBe -5
            strtolBase0("+7") shouldBe 7
        }
    })
