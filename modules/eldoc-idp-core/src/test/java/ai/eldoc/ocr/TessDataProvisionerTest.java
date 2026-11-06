/*
 *
 *  * Copyright (C) 2022-2026 elDoc Limited
 *  *
 *  * This file is part of elDoc IDP OSS.
 *  *
 *  * elDoc IDP OSS is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU Affero General Public License as published
 *  * by the Free Software Foundation, version 3.
 *  *
 *  * elDoc IDP OSS is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 *  * General Public License for more details.
 *  *
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */
package ai.eldoc.ocr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;


class TessDataProvisionerTest {

    @TempDir
    Path _tempDir;


    @Test
    void downloadsAndExtractsToCreatedConfiguredPath() throws Exception {
        URL archive = createArchive("tessdata_best-4.1.0/eng.traineddata", "model");
        Path configured = _tempDir.resolve("configured").resolve("tessdata");

        Path result = provisioner(archive).ensureTessData(configured.toString(), _tempDir.resolve("fallback"));

        assertEquals(configured, result);
        assertEquals("model", Files.readString(result.resolve("eng.traineddata")));
        assertFalse(Files.exists(result.resolve("tessdata_best-4.1.0")));
    }


    @Test
    void downloadsIntoExistingEmptyConfiguredDirectory() throws Exception {
        URL archive = createArchive("tessdata_best-4.1.0/eng.traineddata", "model");
        Path configured = Files.createDirectory(_tempDir.resolve("empty-configured"));

        Path result = provisioner(archive).ensureTessData(configured.toString(), _tempDir.resolve("fallback"));

        assertEquals(configured, result);
        assertEquals("model", Files.readString(result.resolve("eng.traineddata")));
        assertFalse(Files.exists(_tempDir.resolve("fallback")));
    }


    @Test
    void usesFallbackForBlankOrUnusableConfiguredPath() throws Exception {
        URL archive = createArchive("tessdata_best-4.1.0/eng.traineddata", "model");
        Path fallback = _tempDir.resolve("fallback");
        Path blankResult = provisioner(archive).ensureTessData("  ", fallback);
        Path invalidConfiguredPath = Files.writeString(_tempDir.resolve("not-a-directory"), "file");
        Path invalidResult = provisioner(archive).ensureTessData(invalidConfiguredPath.toString(), fallback.resolveSibling("fallback-2"));

        assertEquals(fallback, blankResult);
        assertEquals("model", Files.readString(blankResult.resolve("eng.traineddata")));
        assertEquals(fallback.resolveSibling("fallback-2"), invalidResult);
        assertEquals("model", Files.readString(invalidResult.resolve("eng.traineddata")));
    }


    @Test
    void reusesNonemptyConfiguredDirectoryWithoutDownloading() throws Exception {
        Path configured = Files.createDirectories(_tempDir.resolve("existing"));
        Files.writeString(configured.resolve("eng.traineddata"), "existing model");
        URL unavailable = URI.create("http://127.0.0.1:1/unavailable.zip").toURL();

        Path result = provisioner(unavailable).ensureTessData(configured.toString(), _tempDir.resolve("fallback"));

        assertEquals(configured, result);
        assertEquals("existing model", Files.readString(configured.resolve("eng.traineddata")));
        assertFalse(Files.exists(_tempDir.resolve("fallback")));
    }


    @Test
    void reusesNonemptyFallbackDirectory() throws Exception {
        Path fallback = Files.createDirectories(_tempDir.resolve("fallback"));
        Files.writeString(fallback.resolve("eng.traineddata"), "cached model");

        Path result = provisioner(URI.create("http://127.0.0.1:1/unavailable.zip").toURL())
                .ensureTessData("", fallback);

        assertEquals(fallback, result);
        assertEquals("cached model", Files.readString(result.resolve("eng.traineddata")));
    }


    @Test
    void rejectsUnsafeArchiveEntriesAndLeavesDestinationEmpty() throws Exception {
        URL archive = createArchive("tessdata_best-4.1.0/../outside.txt", "unsafe");
        Path destination = _tempDir.resolve("destination");

        assertThrows(IOException.class, () -> provisioner(archive).ensureTessData(destination.toString(), _tempDir.resolve("fallback")));

        assertTrue(Files.isDirectory(destination));
        try (var entries = Files.list(destination)) {
            assertTrue(entries.findAny().isEmpty());
        }
        assertFalse(Files.exists(_tempDir.resolve("outside.txt")));
        assertNoStagingDirectories();
    }


    @Test
    void rejectsCorruptArchivesAndCleansStagingFiles() throws Exception {
        Path corruptArchive = Files.writeString(_tempDir.resolve("corrupt.zip"), "not a zip archive");
        Path destination = _tempDir.resolve("destination");

        assertThrows(IOException.class, () -> provisioner(corruptArchive.toUri().toURL())
                .ensureTessData(destination.toString(), _tempDir.resolve("fallback")));

        assertTrue(Files.isDirectory(destination));
        try (var entries = Files.list(destination)) {
            assertTrue(entries.findAny().isEmpty());
        }
        assertNoStagingDirectories();
    }


    @Test
    void reportsDownloadProgressAtConfiguredIntervals() throws Exception {
        byte[] model = new byte[8 * 1024];
        new Random(17).nextBytes(model);
        URL archive = createArchive("tessdata_best-4.1.0/eng.traineddata", model);
        List<String> progress = new ArrayList<>();
        long archiveSize = Files.size(Path.of(archive.toURI()));

        provisioner(archive, 256, progress::add).ensureTessData("", _tempDir.resolve("fallback"));

        List<String> expectedProgress = new ArrayList<>();
        long totalMegabytes = 1 + (archiveSize - 1) / (1024 * 1024);
        for (long nextProgressBytes = 256; nextProgressBytes <= archiveSize; nextProgressBytes += 256) {
            long downloadedMegabytes = nextProgressBytes / (1024 * 1024);
            long percentage = Math.min(100, Math.round((double) nextProgressBytes * 100 / archiveSize));
            expectedProgress.add("TessData download: " + downloadedMegabytes + " MB (" + percentage
                    + "%) downloaded of total " + totalMegabytes + " MB");
        }
        assertEquals(expectedProgress, progress);
    }


    @Test
    void usesEstimatedTotalWhenContentLengthIsUnknownOrZero() throws Exception {
        URL archive = createArchive("tessdata_best-4.1.0/eng.traineddata", "model");
        byte[] archiveBytes = Files.readAllBytes(Path.of(archive.toURI()));

        for (long contentLength : new long[]{-1, 0}) {
            List<String> progress = new ArrayList<>();
            URL inMemoryArchive = inMemoryArchive(archiveBytes, contentLength);

            provisioner(inMemoryArchive, 32, progress::add)
                    .ensureTessData("", _tempDir.resolve("fallback-" + contentLength));

            assertFalse(progress.isEmpty());
            assertEquals("TessData download: 0 MB (0%) downloaded of total 1400 MB", progress.getFirst());
        }
    }


    @Test
    void reportsDownloadFailuresAndLeavesDestinationEmpty() throws Exception {
        Path destination = _tempDir.resolve("destination");

        assertThrows(IOException.class, () -> provisioner(URI.create("http://127.0.0.1:1/unavailable.zip").toURL())
                .ensureTessData(destination.toString(), _tempDir.resolve("fallback")));

        assertTrue(Files.isDirectory(destination));
        try (var entries = Files.list(destination)) {
            assertTrue(entries.findAny().isEmpty());
        }
        assertNoStagingDirectories();
    }


    private TessDataProvisioner provisioner(URL archive) {
        return provisioner(archive, TessDataProvisioner.PROGRESS_INTERVAL_BYTES, message -> {
        });
    }


    private TessDataProvisioner provisioner(URL archive, long progressInterval, java.util.function.Consumer<String> progressOutput) {
        return new TessDataProvisioner(archive, progressInterval, progressOutput);
    }


    private URL createArchive(String entryName, String content) throws IOException {
        return createArchive(entryName, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }


    private URL createArchive(String entryName, byte[] content) throws IOException {
        Path archive = _tempDir.resolve("tessdata.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(content);
            output.closeEntry();
        }
        return archive.toUri().toURL();
    }


    private URL inMemoryArchive(byte[] archiveBytes, long contentLength) throws IOException {
        return URL.of(URI.create("memory://tessdata.zip"), new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {
                        connected = true;
                    }

                    @Override
                    public InputStream getInputStream() {
                        connect();
                        return new ByteArrayInputStream(archiveBytes);
                    }

                    @Override
                    public long getContentLengthLong() {
                        return contentLength;
                    }
                };
            }
        });
    }


    private void assertNoStagingDirectories() throws IOException {
        try (var entries = Files.list(_tempDir)) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith(".eldoc-tessdata-stage-")));
        }
    }
}
