package me.pinfort.tsselect.cli

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

private class CliResult(
    val code: Int,
    val out: String,
    val err: String,
)

// stderr carries the progress bar, which is repainted with carriage returns.
// Tests care about the messages, so drop the repaints.
private fun String.withoutProgressBar(): String =
    split('\r').filterNot { it.startsWith("processing: ") && !it.startsWith("processing: finish") }.joinToString("")

class CliRunTest :
    StringSpec({
        fun tempDir(): File {
            val dir = File("build/test-tmp/cli")
            dir.mkdirs()
            return dir
        }

        fun tsFile(
            name: String,
            content: ByteArray,
        ): File {
            val file = File(tempDir(), name)
            file.writeBytes(content)
            return file
        }

        // A 188-byte stream with two PIDs, built the way the C tool expects.
        fun sampleStream(): ByteArray {
            val out = ByteArrayOutputStream()
            for (cc in 0 until 16) {
                for (pid in listOf(0x100, 0x101)) {
                    val p = ByteArray(188)
                    p[0] = 0x47
                    p[1] = ((pid shr 8) and 0x1f).toByte()
                    p[2] = (pid and 0xff).toByte()
                    p[3] = ((1 shl 4) or (cc and 0x0f)).toByte()
                    out.write(p)
                }
            }
            return out.toByteArray()
        }

        fun run(vararg args: String): CliResult {
            val outBuf = ByteArrayOutputStream()
            val errBuf = ByteArrayOutputStream()
            val originalOut = System.out
            val originalErr = System.err
            System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
            System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))
            val code =
                try {
                    runCli(arrayOf(*args))
                } finally {
                    System.setOut(originalOut)
                    System.setErr(originalErr)
                }
            return CliResult(code, outBuf.toString(Charsets.UTF_8), errBuf.toString(Charsets.UTF_8))
        }

        "no arguments prints usage and exits 1" {
            val r = run()

            r.code shouldBe 1
            r.out shouldBe ""
            r.err shouldStartWith "tsselect - MPEG-2 TS stream(pid) selector ver. 0.1.8\n"
            r.err shouldContain "usage: tsselect src.m2t [dst.m2t pid  [more pid ..]]\n"
        }

        "dump prints the report on stdout and the bar on stderr" {
            val src = tsFile("dump.ts", sampleStream())

            val r = run(src.path)

            r.code shouldBe 0
            r.out shouldBe
                "pid=0x0100, total=      16, d=  0, e=  0, scrambling=0, offset=0\n" +
                "pid=0x0101, total=      16, d=  0, e=  0, scrambling=0, offset=188\n"
            r.err.withoutProgressBar() shouldBe "processing: finish\n"
        }

        "a missing source is reported with the path as given" {
            val r = run("build/test-tmp/cli/does-not-exist.ts")

            r.code shouldBe 0
            r.out shouldBe ""
            r.err shouldBe "error - failed on open(build/test-tmp/cli/does-not-exist.ts) [src]\n"
        }

        "the source path is printed unnormalised" {
            val r = run("build/test-tmp/cli//does-not-exist.ts")

            r.err shouldBe "error - failed on open(build/test-tmp/cli//does-not-exist.ts) [src]\n"
        }

        "a non ts input is reported as a unit size failure" {
            val src = tsFile("garbage.bin", ByteArray(4096))

            val r = run(src.path)

            r.code shouldBe 0
            r.out shouldBe ""
            r.err shouldBe "error - failed on select_unit_size()\n"
        }

        "an invalid option prints the option error then usage and exits 1" {
            val src = tsFile("opt.ts", sampleStream())

            val r = run(src.path, File(tempDir(), "opt-out.ts").path, "-q", "0x100")

            r.code shouldBe 1
            r.err shouldStartWith "error - invalid option '-q'\n"
            r.err shouldContain "usage: tsselect"
        }

        "a bare dash reports the C quirk of a blank option letter" {
            val src = tsFile("dash.ts", sampleStream())

            val r = run(src.path, File(tempDir(), "dash-out.ts").path, "-", "0x100")

            r.code shouldBe 1
            r.err shouldStartWith "error - invalid option '- '\n"
        }

        "remux writes only the selected pid" {
            val src = tsFile("sel.ts", sampleStream())
            val dst = File(tempDir(), "sel-out.ts")
            dst.delete()

            val r = run(src.path, dst.path, "0x100")

            r.code shouldBe 0
            r.out shouldBe ""
            r.err.withoutProgressBar() shouldBe "processing: finish\n"
            dst.length() shouldBe 16L * 188
        }

        "exclude remux drops only the listed pid" {
            val src = tsFile("excl.ts", sampleStream())
            val dst = File(tempDir(), "excl-out.ts")
            dst.delete()

            run(src.path, dst.path, "-x", "0x100").code shouldBe 0

            dst.length() shouldBe 16L * 188
        }

        "a missing source in remux mode never creates the destination" {
            val dst = File(tempDir(), "never-created.ts")
            dst.delete()

            val r = run("build/test-tmp/cli/missing-src.ts", dst.path, "0x100")

            r.code shouldBe 0
            r.err shouldBe "error - failed on open(build/test-tmp/cli/missing-src.ts) [src]\n"
            dst.exists() shouldBe false
        }

        "an unopenable destination is reported as a destination failure" {
            val src = tsFile("dstfail.ts", sampleStream())

            val r = run(src.path, "build/test-tmp/cli/no-such-dir/out.ts", "0x100")

            r.code shouldBe 0
            r.err shouldBe "error - failed on open(build/test-tmp/cli/no-such-dir/out.ts) [dst]\n"
        }

        "a non ts input in remux mode still creates the destination as C does" {
            val src = tsFile("garbage2.bin", ByteArray(4096))
            val dst = File(tempDir(), "garbage-out.ts")
            dst.delete()

            val r = run(src.path, dst.path, "0x100")

            r.code shouldBe 0
            r.err shouldBe "error - failed on select_unit_size()\n"
            dst.exists() shouldBe true
            dst.length() shouldBe 0L
        }
    })
