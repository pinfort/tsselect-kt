package me.pinfort.tsselect.cli

import me.pinfort.tsselect.TsDump
import me.pinfort.tsselect.TsFormatException
import me.pinfort.tsselect.format
import me.pinfort.tsselect.pidMapOf
import me.pinfort.tsselect.tsSelect
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        showUsage()
        exitProcess(1)
    }

    if (args.size == 1) {
        runDump(args[0])
    } else {
        val parsed = parseArgs(args)
        if (parsed == null) {
            showUsage()
            exitProcess(1)
        }
        runSelect(parsed)
    }

    exitProcess(0)
}

private fun runDump(src: String) {
    val dump = TsDump()
    try {
        dump.dump(File(src), StderrProgressListener())
    } catch (_: TsFormatException) {
        System.err.print("error - failed on select_unit_size()\n")
        return
    } catch (_: IOException) {
        System.err.print("error - failed on open(%s) [src]\n".format(Locale.ROOT, src))
        return
    }
    print(dump.report().format())
}

private fun runSelect(parsed: ParsedArgs) {
    val srcFile = File(parsed.src)
    val input = try {
        FileInputStream(srcFile)
    } catch (_: IOException) {
        System.err.print("error - failed on open(%s) [src]\n".format(Locale.ROOT, parsed.src))
        return
    }
    input.use { ins ->
        val output = try {
            BufferedOutputStream(FileOutputStream(parsed.dst), 65536)
        } catch (_: IOException) {
            System.err.print("error - failed on open(%s) [dst]\n".format(Locale.ROOT, parsed.dst))
            return
        }
        try {
            tsSelect(ins, output, srcFile.length(), parsed.pidMap, StderrProgressListener())
        } catch (_: TsFormatException) {
            System.err.print("error - failed on select_unit_size()\n")
        } catch (_: IOException) {
            System.err.print("error - failed on write() [dst]\n")
        } finally {
            // a failed write was already reported above; C ignores _close
            // errors, so swallow them here too
            try {
                output.close()
            } catch (_: IOException) {
            }
        }
    }
}

class ParsedArgs(val src: String, val dst: String, val pidMap: ByteArray)

// Port of the argv loop: args[0]=src, args[1]=dst, the rest are PIDs in
// strtol(.., 0) notation plus an optional -x/-X to invert the selection.
// Returns null on an invalid option, after printing the C error message.
fun parseArgs(args: Array<String>): ParsedArgs? {
    val pids = ArrayList<Int>()
    var exclude = false

    for (i in 2 until args.size) {
        val arg = args[i]

        if (arg.startsWith("-")) {
            val c = if (arg.length > 1) arg[1] else ' '
            if (c == 'x' || c == 'X') {
                exclude = true
            } else {
                System.err.print("error - invalid option '-%c'\n".format(Locale.ROOT, c))
                return null
            }
            continue
        }

        pids.add(strtolBase0(arg))
    }

    return ParsedArgs(args[0], args[1], pidMapOf(pids, exclude))
}

private fun showUsage() {
    System.err.print("tsselect - MPEG-2 TS stream(pid) selector ver. 0.1.8\n")
    System.err.print("usage: tsselect src.m2t [dst.m2t pid  [more pid ..]]\n")
    System.err.print("\n")
    System.err.print("ex: dump \"src.m2t\" TS information\n")
    System.err.print("  tsselect src.m2t\n")
    System.err.print("\n")
    System.err.print("ex: remux \"src.m2t\" to \"dst.m2t\" which contains pid=0x1000 and pid=0x1001\n")
    System.err.print("  tsselect src.m2t dst.m2t 0x1000 0x1001\n")
    System.err.print("\n")
    System.err.print("ex: remux \"src.m2t\" to \"dst.m2t\" exclude pid=0x0012(EIT) pid=0x0014(TOT)\n")
    System.err.print("  tsselect src.m2t dst.m2t -x 0x0012 0x0014\n")
    System.err.print("\n")
}

// Emulates C strtol(s, NULL, 0): optional whitespace and sign, 0x/0X prefix
// selects hex, a leading 0 selects octal, otherwise decimal; parses the
// longest valid prefix and returns 0 when no digits are found.
fun strtolBase0(s: String): Int {
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

private fun digitValue(c: Char, base: Int): Int {
    val d = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'z' -> c - 'a' + 10
        in 'A'..'Z' -> c - 'A' + 10
        else -> return -1
    }
    return if (d < base) d else -1
}
