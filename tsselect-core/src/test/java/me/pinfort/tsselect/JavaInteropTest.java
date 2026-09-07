package me.pinfort.tsselect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Exercises the {@code @Jvm*}-annotated public surface of {@code tsselect-core}
 * the way a Java consumer would. The Kotlin specs cannot regression-test this:
 * only Java source that resolves {@code TsDump.tsDump(file)},
 * {@code PidSelection.of(pids)} without {@code .Companion.}, the {@code NONE}
 * fields, and the {@code @JvmOverloads}-generated short forms will fail to
 * compile if one of those annotations is dropped.
 */
class JavaInteropTest {

    private static final int PACKET_COUNT = 16;

    /** A synthetic 188-byte-grid stream: each packet is 0x47 then zeros. */
    private static byte[] cleanStream() {
        byte[] buf = new byte[PACKET_COUNT * 188];
        for (int i = 0; i < PACKET_COUNT; i++) {
            buf[i * 188] = 0x47;
        }
        return buf;
    }

    /** Project-relative scratch file, matching the Kotlin specs' `tempFile`. */
    private static File tempFile(String name) throws IOException {
        File dir = new File("build/test-tmp");
        dir.mkdirs();
        File file = new File(dir, name);
        Files.write(file.toPath(), cleanStream());
        return file;
    }

    @Test
    void tsDumpStreamOverloads() {
        byte[] bytes = cleanStream();

        // @JvmOverloads short form: no ProgressListener argument.
        TsDumpReport report = TsDump.tsDump(new ByteArrayInputStream(bytes), bytes.length);
        assertFalse(report.getPids().isEmpty());
        assertTrue(report.format().contains("pid="));

        // Full form with the ProgressListener.NONE static field.
        TsDumpReport again = TsDump.tsDump(new ByteArrayInputStream(bytes), bytes.length, ProgressListener.NONE);
        assertEquals(report.getPids().size(), again.getPids().size());

        // A lambda for the fun interface; Progress getters are Java-shaped.
        AtomicInteger calls = new AtomicInteger();
        TsDump.tsDump(
            new ByteArrayInputStream(bytes),
            bytes.length,
            progress -> {
                calls.incrementAndGet();
                assertTrue(progress.getBytesProcessed() >= 0);
                progress.getBasisPoints();
            });
        assertTrue(calls.get() > 0);
    }

    @Test
    void tsDumpFileOverload() throws IOException {
        TsDumpReport report = TsDump.tsDump(tempFile("java-interop-dump.ts"));
        assertFalse(report.getPids().isEmpty());
    }

    @Test
    void tsDumpRejectsNonTs() {
        InputStream garbage = new ByteArrayInputStream(new byte[4096]);
        assertThrows(TsFormatException.class, () -> TsDump.tsDump(garbage, 4096));
    }

    @Test
    void pidSelectionFactoriesAreStatic() {
        // No .Companion. — @JvmStatic.
        PidSelection byNumber = PidSelection.of(List.of(0x100, 0x101));
        PidSelection byToken = PidSelection.parse(List.of("0x100", "0x101"));
        assertEquals(byNumber, byToken);
        assertEquals(2, byNumber.getSize());
        assertTrue(byNumber.contains(0x100));

        // @JvmOverloads short forms: no exclude argument.
        assertEquals(PidSelection.of(List.of(0x100)), PidSelection.of(List.of(0x100), false));

        // @JvmField constants, not getNONE()/getALL().
        assertEquals(0, PidSelection.NONE.getSize());
        assertEquals(PidSelection.PID_COUNT, PidSelection.ALL.getSize());
    }

    @Test
    void tsSelectStreamOverload() {
        byte[] bytes = cleanStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // @JvmOverloads short form: no ProgressListener argument.
        TsSelectResult result = TsSelect.tsSelect(
            new ByteArrayInputStream(bytes),
            out,
            bytes.length,
            PidSelection.of(List.of(0)));

        assertEquals(188, result.getUnitSize());
        assertTrue(result.getPacketsRead() > 0);
        assertEquals(result.getPacketsRead(), result.getPacketsWritten());
        assertEquals(result.getPacketsWritten() * 188, out.size());
    }

    @Test
    void tsSelectFileOverload() throws IOException {
        File src = tempFile("java-interop-select-src.ts");
        File dst = new File(src.getParentFile(), "java-interop-select-dst.ts");

        TsSelectResult result = TsSelect.tsSelect(src, dst, PidSelection.ALL);

        assertNotNull(result);
        assertTrue(dst.length() > 0);
    }
}
