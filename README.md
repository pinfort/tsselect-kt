# tsselect

A Kotlin/JVM port of the classic `tsselect` MPEG-2 TS analyzer and stream (PID) selector.

The project ships two things from one build:

| Module | What it is |
| --- | --- |
| `tsselect-core` | The library — TS packet parsing, sync recovery, drop/continuity analysis, PID remuxing. No printing, no `exitProcess`. Published as `me.pinfort:tsselect`. |
| `tsselect-cli` | The `tsselect` executable — argument parsing, the stderr progress bar, and the report output. |

## The executable

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

```bash
./gradlew :tsselect-core:publishToMavenLocal
```

```kotlin
repositories { mavenLocal() }
dependencies { implementation("me.pinfort:tsselect:1.0-SNAPSHOT") }
```

Analyzing a stream. `report()` hands back a data model; `format()` renders it in the
C tool's exact output shape if you want that:

```kotlin
val dump = TsDump()
dump.dump(File("src.m2ts"))

val report = dump.report()
report.pids.forEach { println("pid ${it.pid}: ${it.total} packets, ${it.drop} drops") }
println(report.format())   // the classic "pid=0x0100, total=..." lines
```

Selecting PIDs. Both a file and a stream overload exist; the stream one never
touches the filesystem:

```kotlin
// keep two PIDs, writing 188-byte packets (192/204-byte trailers are stripped)
tsSelect(File("src.m2ts"), File("dst.ts"), pidMapOf(listOf(0x1000, 0x1001)))

// or drop EIT and TOT, in memory
val out = ByteArrayOutputStream()
File("src.m2ts").inputStream().use {
    tsSelect(it, out, File("src.m2ts").length(), pidMapOf(listOf(0x0012, 0x0014), exclude = true))
}
```

Progress and errors are the caller's to handle. Pass a `ProgressListener` for
long inputs, and expect exceptions rather than messages on stderr:
`TsFormatException` when the input has no recognizable 188/192/204-byte packet
grid, and plain `IOException`/`FileNotFoundException` for IO failures.

```kotlin
dump.dump(file, object : ProgressListener {
    override fun onProgress(processed: Long, total: Long) = renderBar(processed, total)
    override fun onFinish() = clearBar()
})
```

## Building

```bash
./gradlew build
```

Requires a JDK 25 toolchain (Gradle provisions it via the foojay resolver).

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
behavior deliberately — including its quirks. One difference from the original readme's
sample output: the C source at 0.1.8 also prints an `offset=` field on each PID line, which
that sample predates; this port matches the source, not the sample.
