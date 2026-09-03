# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Kotlin/JVM port of `tsselect` ver. 0.1.8 by 茂木和洋 (Mogi Kazuhiro) — an MPEG-2 TS
analyzer and stream (PID) selector. The port is **deliberately bit-faithful to the C
original**, including its quirks and bugs. The reference C source is kept at the repo root
as `tsselect.c` (gitignored); consult it before changing any parsing or accounting logic.
Many comments in the Kotlin code cite line numbers in `tsselect.c`.

## Commands

```bash
./gradlew build                 # compile + test + lint everything
./gradlew test                  # all tests (JUnit 5)
./gradlew ktlintCheck           # lint
./gradlew ktlintFormat          # autoformat (kotlin.code.style=official)

# aggregated coverage (Kover) -> build/reports/kover/{report.xml,html/}
./gradlew koverHtmlReport koverXmlReport

# single test class / method
./gradlew :tsselect-core:test --tests 'me.pinfort.tsselect.SyncTest'
./gradlew :tsselect-core:test --tests '*.TsDumpIntegrationTest.dumpsCleanSinglePidStream'

# run / install the CLI
./gradlew :tsselect-cli:run --args="src.m2ts"
./gradlew :tsselect-cli:installDist   # launcher: tsselect-cli/build/install/tsselect/bin/tsselect

# publish the library locally (artifact me.pinfort:tsselect)
./gradlew :tsselect-core:publishToMavenLocal
```

Requires a JDK 25 toolchain; Gradle provisions it via the foojay resolver (needs network on
first run). Gradle 9.6 via the wrapper. `tsselect-core` is a published library, so it compiles
with the JDK 25 toolchain but emits **Java 17 bytecode** (`jvmTarget` + `targetCompatibility`);
`tsselect-cli` is an application and stays on 25.

## Architecture

Two modules, one build:

- **`tsselect-core`** — the library. Pure analysis and remuxing. It never prints, never
  calls `exitProcess`, and signals failure only by throwing (`TsFormatException` for a
  non-TS input, `IOException`/`FileNotFoundException` for IO). Published as `me.pinfort:tsselect`.
- **`tsselect-cli`** — the `tsselect` executable. Owns everything terminal-shaped: argument
  parsing, usage text, the stderr progress bar, C-style `error - ...` messages, exit codes.

### Processing pipeline (core)

1. **Grid detection** — `selectUnitSize` (`Sync.kt`) histograms `0x47`-to-`0x47` strides to
   decide whether packets are 188 (TS), 192 (M2TS) or 204 (Reed–Solomon) bytes.
2. **Chunk loop** — `TsDumpEngine.run` and `tsSelect` (`TsDumpEngine.kt`, `TsSelect.kt`) read the input
   in 8192-byte buffers and walk packets at `unitSize` stride. On lost alignment they call
   `resync` (needs 8 consecutive sync bytes at stride) or, for the buffer tail, `resyncForce`
   (`Sync.kt`). The two loop bodies are near-identical by design — they mirror the C source.
3. **Packet parse** — `TsHeader.parse` and `AdaptationField.parse` (`Packet.kt`) decode into
   the mutable structs in `Models.kt`. A malformed adaptation field zeroes the whole struct,
   exactly as the C code does.
4. **Accounting** (dump only) — `TsDumpEngine.processPacket` maintains a `TsStatus` per PID (0..8191)
   with the C tool's continuity-counter / duplicate / drop logic verbatim. Drop positions are
   attributed to the current resync entry (`RESYNC_LOG_MAX` = 8, ≤ 4 drops recorded each).
5. **Report** — `TsDumpEngine.report()` produces an immutable `TsDumpReport` data model
   (`Report.kt`), which the public `tsDump()` returns; `TsDumpReport.format()` renders it as the
   C tool's exact text. Formatting is separate from the model so the library stays output-free.

### Key seams

The public surface of `tsselect-core` is deliberately small: four entry points (`tsDump` ×2,
`tsSelect` ×2), the `PidSelection` / `Progress` / `TsDumpReport` / `TsSelectResult` value types,
the `TsException` hierarchy, and `TsDumpReport.format()`. Everything else — `Sync.kt`, `Io.kt`,
`Packet.kt`, `Models.kt`, `TsDumpEngine`, `strtolBase0` — is `internal`. Core's test source set is
a friend of its main source set, so specs still see all of it; keep new implementation detail
`internal` rather than public.

- **`TsException`** (`TsException.kt`) is a sealed hierarchy with exactly four cases, isomorphic to
  the C tool's four error strings: `TsSourceOpenException`, `TsDestinationOpenException`,
  `TsFormatException`, `TsWriteException`. Core wraps every open/write so a public entry point never
  throws a bare `IOException`, which is what lets the CLI map failures with one exhaustive `when`
  (`errorMessage` in `cli/Main.kt`) instead of order-dependent `catch` clauses. There is
  deliberately **no** read-failure case: `readFully` treats a read error as EOF, as C treats
  `_read < 1`.
- **`ProgressListener`** (`ProgressListener.kt`) is the only callback into caller code from the chunk
  loops. It is a `fun interface` taking a single `Progress`; the finish event is `Progress.finished`
  rather than a second method. The library fires once per read chunk with no throttling —
  `StderrProgressListener` in the CLI repaints on `chunkIndex and 0x0f == 0`, so the repaint rate no
  longer depends on core's private 8192-byte buffer. `Progress.basisPoints` is the C tool's
  `(int)(10000 * offset / total)` and returns 0 for an unknown total instead of dividing by zero.
- **File vs. stream overloads** — `tsDump` and `tsSelect` each have a `File` overload (opens/closes
  the fds itself, and is the *only* place that dance lives) and a stream overload (caller owns the
  streams, no filesystem access). The stream overload takes `totalBytes` only to compute the progress
  fraction. In the `File` overload of `tsSelect`, **src must be opened before dst and the opens must
  stay nested**: a source that cannot be read has to leave dst untouched, as in C. A format failure,
  by contrast, happens after dst is opened, so C *does* leave an empty dst behind — both are pinned
  by tests.
- **PID selection** — `PidSelection` (`PidSelection.kt`) is an immutable, bounds-checked set with a
  private `ByteArray(8192)` inside; `tsSelect` asks `header.pid in pids`. `PidSelection.of` takes
  parsed `Int`s, `PidSelection.parse` takes argv-shaped strings. `exclude = true` inverts. Remux
  always writes 188 bytes, stripping 192/204 trailers/headers.
- **`strtolBase0`** (`Strtol.kt`, `internal`) reimplements C `strtol(s, NULL, 0)` so PID arguments
  accept `0x`/octal/decimal with the same edge cases as the original. It lives in core, next to the
  0..8191 range check in `PidSelection.of`, so the whole number rule is in one module; the CLI owns
  only the *option* grammar (`-x`/`-X` and the `invalid option` message).
- **`runCli`** (`cli/Main.kt`) is `main` minus `exitProcess`, so `CliRunTest` can drive the entire CLI
  and assert exit codes and exact stdout/stderr. `errorMessage` formats the **argv** strings, never
  `TsException.path` — `java.io.File` normalises `dir//file` to `dir/file` and C does not.

## Fidelity notes (do not "fix" these)

- `AdaptationField.discontinuityIndicator` is named after C's `discontinuity_counter` field
  but holds a single indicator bit.
- PCR / OPCR low-bit packing in `AdaptationField.parse` reproduces the C source's unusual bit
  layout on purpose.
- The port prints `offset=` on each PID line — matching `tsselect.c` at 0.1.8, not the older
  sample output in the original readme.
- `TsDumpEngine.run` and `tsSelect` are near-identical **by design** — they mirror two separate C
  functions with different resync bookkeeping (dump records `miss`/`sync`/`resyncCount`, select does
  not). Do not deduplicate them.
- The one intentional divergence: with an unknown input size (`totalBytes <= 0`, i.e. a FIFO or
  `/dev/stdin`) C divides by zero computing the percentage; `Progress.basisPoints` returns 0.

## Tests

[Kotest](https://kotest.io) `StringSpec` on the JUnit 5 platform (`kotest-runner-junit5` +
`kotest-assertions-core`, `useJUnitPlatform()`). One spec class per source file, plus
`*IntegrationTest` specs; shared helpers (`tempFile`, `feed`, …) are locals inside the spec
lambda. `CliRunTest` drives `runCli` with `System.out`/`System.err` swapped out, which is how the
CLI's exit codes and C-compatible error strings are pinned. All fixtures are synthetic packets from `tsPacket` / `stream` in `TsTestData.kt` —
nothing depends on the large `test.m2ts` at the repo root (a local sample, gitignored).

## CI

`.github/workflows/ci.yml` runs `ktlintCheck`, `test` and `bytecode` as separate jobs on pushes
to `main` and on PRs. The `test` job also generates the Kover coverage report, writes a summary
table to the job summary, and uploads `build/reports/kover/` as the `coverage-report` artifact.
The `bytecode` job compiles `:tsselect-core` and asserts every `.class` file under
`build/classes/kotlin/main` has class-file major version 61, so the "Java 17 bytecode" target
stays enforced rather than only documented.

Kover is applied to each module (`org.jetbrains.kotlinx.kover`) and aggregated by the root
project via `kover(project(...))` dependencies. All repositories are declared once in
`settings.gradle.kts` under `dependencyResolutionManagement` (`FAIL_ON_PROJECT_REPOS`), so no
build script has its own `repositories {}` block.

## Environment gotchas

- Gradle always forks a daemon here (heap requirement). If a build dies with `Timeout waiting
  to lock journal cache`, another Gradle process (often the IDE's) holds it — run
  `./gradlew --stop` and retry.
- If the sandbox has no DNS, route Gradle through the CONNECT proxy from `$HTTPS_PROXY`
  (`localhost:3128`, basic auth) with `-Dhttps.proxyHost/-Dhttps.proxyPort/-Dhttps.proxyUser/
  -Dhttps.proxyPassword -Djdk.http.auth.tunneling.disabledSchemes=`. Once dependencies are
  cached in `~/.gradle`, `--offline` works.
