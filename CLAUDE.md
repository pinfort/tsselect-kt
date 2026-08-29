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
first run). Gradle 9.6 via the wrapper.

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
2. **Chunk loop** — `TsDump.dump` and `tsSelect` (`TsDump.kt`, `TsSelect.kt`) read the input
   in 8192-byte buffers and walk packets at `unitSize` stride. On lost alignment they call
   `resync` (needs 8 consecutive sync bytes at stride) or, for the buffer tail, `resyncForce`
   (`Sync.kt`). The two loop bodies are near-identical by design — they mirror the C source.
3. **Packet parse** — `TsHeader.parse` and `AdaptationField.parse` (`Packet.kt`) decode into
   the mutable structs in `Models.kt`. A malformed adaptation field zeroes the whole struct,
   exactly as the C code does.
4. **Accounting** (dump only) — `TsDump.processPacket` maintains a `TsStatus` per PID (0..8191)
   with the C tool's continuity-counter / duplicate / drop logic verbatim. Drop positions are
   attributed to the current resync entry (`RESYNC_LOG_MAX` = 8, ≤ 4 drops recorded each).
5. **Report** — `TsDump.report()` produces an immutable `TsDumpReport` data model (`Report.kt`);
   `TsDumpReport.format()` renders it as the C tool's exact text. Formatting is separate from
   the model so the library stays output-free.

### Key seams

- **`ProgressListener`** (`ProgressListener.kt`) is the only callback into caller code from
  the chunk loops. The library fires `onProgress` once per read chunk with no throttling;
  `StderrProgressListener` in the CLI adds the "repaint every 16th chunk" behavior.
- **File vs. stream overloads** — `TsDump.dump` and `tsSelect` each have a `File` overload
  (opens/closes the fd itself) and a stream overload (caller owns the streams, no filesystem
  access). The stream overload takes `totalBytes` only to compute the progress fraction.
- **PID selection** — `pidMapOf` (`PidMap.kt`) builds the `ByteArray(8192)` map `tsSelect`
  consumes; `exclude = true` inverts it. Remux always writes 188 bytes, stripping 192/204
  trailers/headers.
- **`strtolBase0`** (`cli/Main.kt`) reimplements C `strtol(s, NULL, 0)` so PID arguments
  accept `0x`/octal/decimal with the same edge cases as the original.

## Fidelity notes (do not "fix" these)

- `AdaptationField.discontinuityIndicator` is named after C's `discontinuity_counter` field
  but holds a single indicator bit.
- PCR / OPCR low-bit packing in `AdaptationField.parse` reproduces the C source's unusual bit
  layout on purpose.
- The port prints `offset=` on each PID line — matching `tsselect.c` at 0.1.8, not the older
  sample output in the original readme.

## Tests

[Kotest](https://kotest.io) `StringSpec` on the JUnit 5 platform (`kotest-runner-junit5` +
`kotest-assertions-core`, `useJUnitPlatform()`). One spec class per source file, plus
`*IntegrationTest` specs; shared helpers (`tempFile`, `feed`, …) are locals inside the spec
lambda. All fixtures are synthetic packets from `tsPacket` / `stream` in `TsTestData.kt` —
nothing depends on the large `test.m2ts` at the repo root (a local sample, gitignored).

## CI

`.github/workflows/ci.yml` runs `ktlintCheck` and `test` as separate jobs on pushes to `main`
and on PRs.

## Environment gotchas

- Gradle always forks a daemon here (heap requirement). If a build dies with `Timeout waiting
  to lock journal cache`, another Gradle process (often the IDE's) holds it — run
  `./gradlew --stop` and retry.
- If the sandbox has no DNS, route Gradle through the CONNECT proxy from `$HTTPS_PROXY`
  (`localhost:3128`, basic auth) with `-Dhttps.proxyHost/-Dhttps.proxyPort/-Dhttps.proxyUser/
  -Dhttps.proxyPassword -Djdk.http.auth.tunneling.disabledSchemes=`. Once dependencies are
  cached in `~/.gradle`, `--offline` works.
