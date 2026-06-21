package io.apicurio.registry.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IoUtilTest {

    private static final long MAX_ENTRY_SIZE = 1024 * 1024;
    private static final long MAX_TOTAL_SIZE = 2 * 1024 * 1024;
    private static final int MAX_ENTRY_COUNT = 10;

    @Test
    void testUnpackToDisk_normalExtraction(@TempDir Path tempDir) throws IOException {
        byte[] zipBytes = createZip(
                new TestEntry("file1.txt", "Hello, world!"),
                new TestEntry("subdir/file2.txt", "Nested content"));

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            IoUtil.unpackToDisk(zip, tempDir, MAX_ENTRY_SIZE, MAX_TOTAL_SIZE, MAX_ENTRY_COUNT);
        }

        assertTrue(Files.exists(tempDir.resolve("file1.txt")));
        assertTrue(Files.exists(tempDir.resolve("subdir/file2.txt")));
        assertEquals("Hello, world!", Files.readString(tempDir.resolve("file1.txt")));
        assertEquals("Nested content", Files.readString(tempDir.resolve("subdir/file2.txt")));
    }

    @Test
    void testUnpackToDisk_exceedsMaxEntrySize(@TempDir Path tempDir) throws IOException {
        byte[] largeContent = new byte[2048];
        byte[] zipBytes = createZip(new TestEntry("large.bin", largeContent));

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            IOException thrown = assertThrows(IOException.class,
                    () -> IoUtil.unpackToDisk(zip, tempDir, 1024, MAX_TOTAL_SIZE, MAX_ENTRY_COUNT));
            assertTrue(thrown.getMessage().contains("entry decompressed size exceeds"));
        }
    }

    @Test
    void testUnpackToDisk_exceedsMaxTotalSize(@TempDir Path tempDir) throws IOException {
        byte[] content = new byte[1024];
        byte[] zipBytes = createZip(
                new TestEntry("a.bin", content),
                new TestEntry("b.bin", content),
                new TestEntry("c.bin", content));

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            IOException thrown = assertThrows(IOException.class,
                    () -> IoUtil.unpackToDisk(zip, tempDir, MAX_ENTRY_SIZE, 2048, MAX_ENTRY_COUNT));
            assertTrue(thrown.getMessage().contains("total decompressed size exceeds"));
        }
    }

    @Test
    void testUnpackToDisk_exceedsMaxEntryCount(@TempDir Path tempDir) throws IOException {
        TestEntry[] entries = new TestEntry[5];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new TestEntry("file" + i + ".txt", "content");
        }
        byte[] zipBytes = createZip(entries);

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            IOException thrown = assertThrows(IOException.class,
                    () -> IoUtil.unpackToDisk(zip, tempDir, MAX_ENTRY_SIZE, MAX_TOTAL_SIZE, 3));
            assertTrue(thrown.getMessage().contains("entry count exceeds"));
        }
    }

    private static byte[] createZip(TestEntry... entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (TestEntry entry : entries) {
                zos.putNextEntry(new ZipEntry(entry.name));
                zos.write(entry.content);
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private static class TestEntry {
        final String name;
        final byte[] content;

        TestEntry(String name, String content) {
            this.name = name;
            this.content = content.getBytes();
        }

        TestEntry(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }
    }
}
