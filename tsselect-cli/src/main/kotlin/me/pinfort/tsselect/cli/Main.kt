package me.pinfort.tsselect.cli

import me.pinfort.tsselect.PidSelection
import me.pinfort.tsselect.TsDestinationOpenException
import me.pinfort.tsselect.TsException
import me.pinfort.tsselect.TsFormatException
import me.pinfort.tsselect.TsSourceOpenException
import me.pinfort.tsselect.TsWriteException
import me.pinfort.tsselect.format
import me.pinfort.tsselect.tsDump
import me.pinfort.tsselect.tsSelect
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runCli(args))
}

// Everything main does apart from exiting, so the whole CLI can be driven from
// a test. Returns the process exit code.
internal fun runCli(args: Array<String>): Int {
    if (args.isEmpty()) {
        showUsage()
        return 1
    }

    if (args.size == 1) {
        runDump(args[0])
        return 0
    }

    val parsed = parseArgs(args)
    if (parsed == null) {
        showUsage()
        return 1
    }
    runSelect(parsed)
    return 0
}

private fun runDump(src: String) {
    val report =
        try {
            tsDump(File(src), StderrProgressListener())
        } catch (e: TsException) {
            System.err.print(errorMessage(e, src, ""))
            return
        }
    print(report.format())
}

private fun runSelect(parsed: ParsedArgs) {
    try {
        tsSelect(File(parsed.src), File(parsed.dst), parsed.pids, StderrProgressListener())
    } catch (e: TsException) {
        System.err.print(errorMessage(e, parsed.src, parsed.dst))
    }
}

// The C tool's four error strings, each written exactly once. The when is
// exhaustive over the sealed TsException, so a new failure mode in the library
// becomes a compile error here rather than the wrong message at runtime.
//
// The paths come from argv rather than from the exception: C prints argv as
// given, while java.io.File normalises a path like "dir//file" to "dir/file".
private fun errorMessage(
    e: TsException,
    src: String,
    dst: String,
): String =
    when (e) {
        is TsFormatException -> "error - failed on select_unit_size()\n"
        is TsSourceOpenException -> "error - failed on open(%s) [src]\n".format(Locale.ROOT, src)
        is TsDestinationOpenException -> "error - failed on open(%s) [dst]\n".format(Locale.ROOT, dst)
        is TsWriteException -> "error - failed on write() [dst]\n"
    }

class ParsedArgs(
    val src: String,
    val dst: String,
    val pids: PidSelection,
)

// Port of the argv loop: args[0]=src, args[1]=dst, the rest are PID tokens plus
// an optional -x/-X to invert the selection. Option grammar is the CLI's
// business; the number grammar belongs to PidSelection.parse.
// Returns null on an invalid option, after printing the C error message.
fun parseArgs(args: Array<String>): ParsedArgs? {
    val tokens = ArrayList<String>()
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

        tokens.add(arg)
    }

    return ParsedArgs(args[0], args[1], PidSelection.parse(tokens, exclude))
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
