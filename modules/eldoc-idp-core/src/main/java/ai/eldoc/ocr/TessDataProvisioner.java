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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * TessDataProvisioner is responsible for downloading and extracting the Tesseract OCR data files.
 */
public final class TessDataProvisioner {

    public static final String DEFAULT_DIRECTORY_NAME = "eldoc-idp-oss-tessdata-best";
    public static final long PROGRESS_INTERVAL_BYTES = 50L * 1024 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(TessDataProvisioner.class);
    private static final String DOWNLOAD_URL = "https://github.com/tesseract-ocr/tessdata_best/archive/refs/tags/4.1.0.zip";
    private static final String ARCHIVE_ROOT = "tessdata_best-4.1.0/";
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024;
    private static final long ESTIMATED_ARCHIVE_SIZE_BYTES = 1_400L * BYTES_PER_MEGABYTE;

    private final URL _downloadUrl;
    private final long _progressIntervalBytes;
    private final Consumer<String> _progressOutput;


    public TessDataProvisioner() {
        try {
            _downloadUrl = URI.create(DOWNLOAD_URL).toURL();
        } catch (java.net.MalformedURLException ex) {
            throw new IllegalStateException("Invalid TessData download URL", ex);
        }
        _progressIntervalBytes = PROGRESS_INTERVAL_BYTES;
        _progressOutput = System.out::println;
    }


    public TessDataProvisioner(URL downloadUrl, long progressIntervalBytes, Consumer<String> progressOutput) {
        _downloadUrl = Objects.requireNonNull(downloadUrl);
        if (progressIntervalBytes <= 0) {
            throw new IllegalArgumentException("Download progress interval must be positive");
        }
        _progressIntervalBytes = progressIntervalBytes;
        _progressOutput = Objects.requireNonNull(progressOutput);
    }


    public Path ensureTessData(String configuredPath, Path fallbackPath) throws IOException {
        Path destination = configuredCandidate(configuredPath);
        if (destination == null) {
            destination = writableCandidate(fallbackPath, true);
        }
        if (destination == null) {
            throw new IllegalStateException("No usable Tesseract language-data directory is available");
        }
        if (containsEntries(destination)) {
            LOG.info("Reusing existing Tesseract language data at {}", destination);
            return destination;
        }

        downloadAndExtract(destination);
        return destination;
    }


    private Path configuredCandidate(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        try {
            return writableCandidate(Path.of(configuredPath), false);
        } catch (InvalidPathException ex) {
            LOG.warn("Configured Tesseract language-data path is invalid; using the temporary directory", ex);
            return null;
        }
    }


    private Path writableCandidate(Path path, boolean required) {
        try {
            Path candidate = path.toAbsolutePath().normalize();
            if (Files.isDirectory(candidate) && containsEntries(candidate)) {
                return candidate;
            }
            if (Files.exists(candidate) && !Files.isDirectory(candidate)) {
                throw new IOException("Path exists but is not a directory: " + candidate);
            }
            Files.createDirectories(candidate);
            Path writeProbe = Files.createTempFile(candidate, ".eldoc-write-test-", ".tmp");
            Files.delete(writeProbe);
            return candidate;
        } catch (IOException | SecurityException ex) {
            if (required) {
                throw new IllegalStateException("Unable to create or write Tesseract language-data directory: " + path, ex);
            }
            LOG.warn("Configured Tesseract language-data directory is unavailable; using the temporary directory: {}", path, ex);
            return null;
        }
    }


    private boolean containsEntries(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isPresent();
        }
    }


    private void downloadAndExtract(Path destination) throws IOException {
        Path stagingDirectory = Files.createTempDirectory(destination.getParent(), ".eldoc-tessdata-stage-");
        try {
            Path archivePath = stagingDirectory.resolve("tessdata.zip");
            Path extractedDirectory = Files.createDirectory(stagingDirectory.resolve("extracted"));
            download(archivePath);
            extract(archivePath, extractedDirectory);
            publish(extractedDirectory, destination);
        } catch (IOException | RuntimeException ex) {
            try {
                deleteRecursively(stagingDirectory);
            } catch (IOException cleanupException) {
                ex.addSuppressed(cleanupException);
            }
            throw ex;
        }
        deleteRecursively(stagingDirectory);
    }


    private void download(Path archivePath) throws IOException {
        URLConnection connection = _downloadUrl.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setUseCaches(false);
        if (connection instanceof HttpURLConnection httpConnection) {
            httpConnection.setRequestProperty("User-Agent", "elDoc-IDP-OSS");
            int responseCode = httpConnection.getResponseCode();
            if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("TessData download failed with HTTP status " + responseCode);
            }
        }

        long totalBytes = connection.getContentLengthLong();
        if (totalBytes <= 0) {
            totalBytes = ESTIMATED_ARCHIVE_SIZE_BYTES;
        }
        long totalMegabytes = 1 + (totalBytes - 1) / BYTES_PER_MEGABYTE;
        long downloadedBytes = 0;
        long nextProgressBytes = _progressIntervalBytes;
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(archivePath))) {
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                downloadedBytes += bytesRead;
                while (downloadedBytes >= nextProgressBytes) {
                    long downloadedMegabytes = nextProgressBytes / BYTES_PER_MEGABYTE;
                    long percentage = Math.min(100, Math.round((double) nextProgressBytes * 100 / totalBytes));
                    _progressOutput.accept("TessData download: " + downloadedMegabytes + " MB (" + percentage
                            + "%) downloaded of total " + totalMegabytes + " MB");
                    nextProgressBytes += _progressIntervalBytes;
                }
            }
        } finally {
            if (connection instanceof HttpURLConnection httpConnection) {
                httpConnection.disconnect();
            }
        }
    }


    private void extract(Path archivePath, Path extractedDirectory) throws IOException {
        boolean foundTrainedData = false;
        try (ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archivePath)))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                String entryName = entry.getName();
                validateEntryName(entryName);
                if (!entryName.startsWith(ARCHIVE_ROOT)) {
                    throw new IOException("Unexpected entry in TessData archive: " + entryName);
                }

                String relativeName = entryName.substring(ARCHIVE_ROOT.length());
                if (relativeName.isEmpty()) {
                    zipInput.closeEntry();
                    continue;
                }
                Path relativePath = Path.of(relativeName).normalize();
                Path outputPath = extractedDirectory.resolve(relativePath).normalize();
                if (relativePath.isAbsolute() || relativePath.startsWith("..") || !outputPath.startsWith(extractedDirectory)) {
                    throw new IOException("Unsafe entry in TessData archive: " + entryName);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Path parent = outputPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(outputPath))) {
                        zipInput.transferTo(output);
                    }
                    if (relativeName.endsWith(".traineddata")) {
                        foundTrainedData = true;
                    }
                }
                zipInput.closeEntry();
            }
        }

        if (!foundTrainedData) {
            throw new IOException("TessData archive contains no .traineddata model files");
        }
    }


    private void validateEntryName(String entryName) throws IOException {
        if (entryName.isBlank() || entryName.startsWith("/") || entryName.contains("\\") || entryName.matches("^[A-Za-z]:.*")) {
            throw new IOException("Unsafe entry in TessData archive: " + entryName);
        }
        for (String part : entryName.split("/")) {
            if (part.equals(".") || part.equals("..")) {
                throw new IOException("Unsafe entry in TessData archive: " + entryName);
            }
        }
    }


    private void publish(Path extractedDirectory, Path destination) throws IOException {
        List<Path> publishedPaths = new ArrayList<>();
        try (Stream<Path> paths = Files.list(extractedDirectory)) {
            for (Path stagedPath : paths.toList()) {
                Path publishedPath = destination.resolve(stagedPath.getFileName());
                Files.move(stagedPath, publishedPath);
                publishedPaths.add(publishedPath);
            }
        } catch (IOException ex) {
            for (int index = publishedPaths.size() - 1; index >= 0; index--) {
                try {
                    deleteRecursively(publishedPaths.get(index));
                } catch (IOException cleanupException) {
                    ex.addSuppressed(cleanupException);
                }
            }
            throw ex;
        }
    }


    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path child : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(child);
            }
        }
    }
}
