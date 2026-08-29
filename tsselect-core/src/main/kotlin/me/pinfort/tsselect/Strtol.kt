package me.pinfort.tsselect

// Emulates C strtol(s, NULL, 0): optional whitespace and sign, 0x/0X prefix
// selects hex, a leading 0 selects octal, otherwise decimal; parses the
// longest valid prefix and returns 0 when no digits are found.
//
// Internal because a C runtime emulation is not part of this library's
// contract; PidSelection.parse() is the entry point for argv-shaped input.
internal fun strtolBase0(s: String): Int {
    var i = 0
    while (i < s.length && s[i].isWhitespace()) {
        i += 1
    }

    var negative = false
    if (i < s.length && (s[i] == '+' || s[i] == '-')) {
        negative = s[i] == '-'
        i += 1
    }

    var base = 10
    if (i < s.length && s[i] == '0') {
        if (i + 2 < s.length && (s[i + 1] == 'x' || s[i + 1] == 'X') && digitValue(s[i + 2], 16) >= 0) {
            base = 16
            i += 2
        } else {
            base = 8
        }
    }

    var value = 0L
    var any = false
    while (i < s.length) {
        val d = digitValue(s[i], base)
        if (d < 0) {
            break
        }
        any = true
        value = value * base + d
        if (value > Int.MAX_VALUE) {
            value = Int.MAX_VALUE.toLong()
        }
        i += 1
    }

    if (!any) {
        return 0
    }
    val v = value.toInt()
    return if (negative) -v else v
}

private fun digitValue(
    c: Char,
    base: Int,
): Int {
    val d =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'z' -> c - 'a' + 10
            in 'A'..'Z' -> c - 'A' + 10
            else -> return -1
        }
    return if (d < base) d else -1
}
