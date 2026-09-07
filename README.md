# tsselect

A Kotlin/JVM port of the classic `tsselect` MPEG-2 TS analyzer and stream (PID) selector.

The project ships two things from one build:

| Module | What it is |
| --- | --- |
| `tsselect-core` | The library — TS packet parsing, sync recovery, drop/continuity analysis, PID remuxing. No printing, no `exitProcess`. Published as `me.pinfort:tsselect`. |
| `tsselect-cli` | The `tsselect` executable — argument parsing, the stderr progress bar, and the report output. |

## Version requirements

| Usage | Java | Kotlin |
| --- | --- | --- |
| Run the CLI (release archive or `installDist`) | 25 runtime on `PATH` / `JAVA_HOME` | — |
| Use the library from Kotlin | 17+ runtime (artifact is Java 17 bytecode; 17, 21, 25 all work) | 2.0+ — `languageVersion`/`apiVersion` and `@Metadata` are pinned to 2.0, so an older compiler needs `-Xskip-metadata-version-check` |
| Use the library from Java | 17+ runtime | none |
| Build from source | JDK 25 (any JDK launches the wrapper; the JDK 25 toolchain is auto-provisioned via the foojay resolver, which needs network on first run) | 2.4, supplied by the Kotlin Gradle plugin |

Gradle 9.6 comes from the wrapper (`./gradlew`) — nothing to install.
`tsselect-core` compiles to Java 17 bytecode with Kotlin language version 2.0;
`tsselect-cli` targets Java 25.

## The executable

Every tagged release carries the CLI distribution on its
[GitHub release page](https://github.com/pinfort/tsselect-kt/releases) as
`tsselect-<version>.zip` / `.tar.gz`, with a `SHA256SUMS.txt` beside them:

```bash
tar xzf tsselect-1.0.0.tar.gz
tsselect-1.0.0/bin/tsselect src.m2t        # `bin/tsselect.bat` on Windows
```

The archive is a plain JVM distribution — the launcher script plus the jars — so its only
requirement is a Java 25 runtime (see [Version requirements](#version-requirements)).

To build it from source instead:

```bash
./gradlew :tsselect-cli:installDist
```

The launcher lands at `tsselect-cli/build/install/tsselect/bin/tsselect` and behaves exactly like the C original:

```
usage: tsselect src.m2t [dst.m2t pid  [more pid ..]]

ex: dump "src.m2t" TS information
  tsselect src.m2t

ex: remux "src.m2t" to "dst.m2t" which contains pid=0x1000 and pid=0x1001
  tsselect src.m2t dst.m2t 0x1000 0x1001

ex: remux "src.m2t" to "dst.m2t" exclude pid=0x0012(EIT) pid=0x0014(TOT)
  tsselect src.m2t dst.m2t -x 0x0012 0x0014
```

To run it straight from the build: `./gradlew :tsselect-cli:run --args="src.m2t"`.

### Reading the dump

Dump mode prints a sync-error section (only when there were sync errors) followed by one
line per PID:

```
total sync error: 3
  resync[0] : miss=0x000000000000, sync=0x000000000059, drop=0
  resync[1] : miss=0x000053be0745, sync=0x000053be0827, drop=11
    drop[0] : pid=0x1008, pos=0x000053be0827
pid=0x0012, total=  128217, d=  2, e=  0, scrambling=0, offset=8388748
```

- `resync[n]` — the stream lost packet alignment at `miss` and was recovered at `sync`,
  with `drop` drops counted afterwards. A `resync[0]` at `miss=0` only means the capture
  did not start on a packet boundary; it does not by itself indicate lost data. At most
  8 sync errors are detailed, with at most 4 drops listed each.
- `total` — packets seen on that PID.
- `d` — packets where a continuity-counter error was detected.
- `e` — packets with a bit error, i.e. error correction failed inside the tuner.
- `scrambling` — encrypted packets.
- `offset` — byte offset where the PID first appeared.

Remuxing a 204-byte (broadcast, Reed-Solomon) or 192-byte (IEEE 1394, with cycle
count/offset) stream writes a standard 188-byte TS: the trailing 16 bytes of a 204-byte
packet and the leading 4 bytes of a 192-byte packet are dropped. This matches the
original's behavior.

## The library

Published to Maven Central as `me.pinfort:tsselect`:

```kotlin
dependencies { implementation("me.pinfort:tsselect:1.0.0") }
```

It targets Java 17 bytecode, so consumers on 17, 21 and 25 can all use it; Kotlin
consumers need Kotlin 2.0+ (see [Version requirements](#version-requirements)).

To try an unreleased change, `./gradlew :tsselect-core:publishToMavenLocal` and add
`mavenLocal()` to your repositories. Cutting an actual release is
[RELEASING.md](RELEASING.md).

Analyzing a stream. `tsDump` returns a data model; `format()` renders it in the
C tool's exact output shape if you want that:

```kotlin
val report = tsDump(File("src.m2ts"))

report.pids.forEach { println("pid ${it.pid}: ${it.total} packets, ${it.drop} drops") }
println(report.format())   // the classic "pid=0x0100, total=..." lines
```

Selecting PIDs. `PidSelection` says which PIDs to keep; both a file and a stream
overload exist, and the stream one never touches the filesystem:

```kotlin
// keep two PIDs, writing 188-byte packets (192/204-byte trailers are stripped)
val result = tsSelect(File("src.m2ts"), File("dst.ts"), PidSelection.of(listOf(0x1000, 0x1001)))
println("${result.packetsWritten} of ${result.packetsRead} packets kept")

// or drop EIT and TOT, in memory
val out = ByteArrayOutputStream()
File("src.m2ts").inputStream().use {
    tsSelect(it, out, File("src.m2ts").length(), PidSelection.of(listOf(0x0012, 0x0014), exclude = true))
}
```

`PidSelection.parse` accepts PID arguments written the way the C tool does —
`0x1000` hex, `0400` octal, `4096` decimal — if you are building a command line
of your own.

Progress and errors are the caller's to handle. Every failure the library
signals is a `TsException`, so a `when` over it is exhaustive:

| Exception | Means |
| --- | --- |
| `TsSourceOpenException` | the source could not be opened; carries `path` |
| `TsDestinationOpenException` | the destination could not be opened; carries `path` |
| `TsFormatException` | the input has no 188/192/204-byte packet grid |
| `TsWriteException` | a write to the destination failed |

A read error is deliberately not among them: the library treats one as EOF,
exactly as the C code treats `_read < 1`.

Pass a `ProgressListener` for long inputs. It is fired once per input chunk and
once more with `finished = true` at the end; a run that fails fires no finish
event. Throttling is yours to do — `chunkIndex` is supplied so you need not know
the library's buffer size:

```kotlin
tsDump(file) { p ->
    if (p.finished) clearBar() else if (p.chunkIndex % 16 == 0) renderBar(p.basisPoints)
}
```

### From Java

The public surface carries `@JvmName` / `@JvmStatic` / `@JvmOverloads` / `@JvmField`,
so it reads the same from Java: the entry points live on `TsDump` / `TsSelect`
(not `TsDumpKt`), the `ProgressListener` argument is optional, `PidSelection`'s
factories are plain statics, and `NONE` / `ALL` are constants:

```java
import me.pinfort.tsselect.*;
import java.io.File;
import java.util.List;

TsDumpReport report = TsDump.tsDump(new File("src.m2ts"));
report.getPids().forEach(p ->
    System.out.println("pid " + p.getPid() + ": " + p.getTotal() + " packets"));
System.out.println(report.format());

PidSelection keep = PidSelection.parse(List.of("0x1000", "0x1001"));
TsSelectResult result = TsSelect.tsSelect(new File("src.m2ts"), new File("dst.ts"), keep);
System.out.println(result.getPacketsWritten() + " of " + result.getPacketsRead() + " kept");

// progress lambda; ProgressListener.NONE is the no-op default
TsDump.tsDump(new File("src.m2ts"), p -> {
    if (!p.getFinished() && p.getChunkIndex() % 16 == 0) renderBar(p.getBasisPoints());
});
```

## Building

```bash
./gradlew build
```

Requires a JDK 25 toolchain (Gradle provisions it via the foojay resolver) — see
[Version requirements](#version-requirements).

## Credits and license

This port is released under the [MIT License](LICENSE). MIT covers the Kotlin source,
tests, and build files in this repository; it does not relicense the original C source,
which is not distributed here. See [NOTICE](NOTICE) for the original terms and the two
conditions they carry.

This is a Kotlin port of `tsselect` ver. 0.1.8 by **茂木和洋 (Mogi Kazuhiro)**, originally
distributed at <http://www.marumo.ne.jp/junk/tsselect-0.1.8.lzh>.

The original `readme.txt` states the terms as follows (quoted verbatim; the headings are
「二次配布等に関して」 and 「ソースコードの利用に関して」):

> オリジナルに改変を加えない場合であれば、その目的・手段を問わず複製・公衆再送信を許可します

> ・ソースコードの利用によってプログラムにバグが混入しても茂木和洋は責任を負わない
> ・ソースコードの利用によって特許関連のトラブルが発生しても茂木和洋は責任を負わない
>
> 上記２条件に同意して作成された二次的著作物に対して、原著作者に与えられる諸権利は行使しません

In summary: redistribution of the unmodified original is permitted regardless of purpose or
means; use of the source code is granted on two conditions — that the original author bears
no responsibility for bugs introduced into your program by using the source, and none for
any patent-related trouble arising from it — and for derivative works created in agreement
with those two conditions, the author does not exercise the rights he holds as the original
author. This port is such a derivative work, published in agreement with those conditions.

The port reproduces the original's output format, drop/continuity accounting, and resync
behavior deliberately — including its quirks. Two differences from the original:

- The original readme's sample output has no `offset=` field on the PID lines. The C source
  at 0.1.8 does print one; this port matches the source, not the sample.
- When the input size is unknown — a FIFO, `/dev/stdin`, a capture still growing — the C
  tool divides by zero computing the progress percentage. This port reports 0% instead.
  Unreachable for a regular file, since a zero-length one fails packet-grid detection first.
