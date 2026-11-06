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
package ai.eldoc.util;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.math.RoundingMode;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * File Utils
 */
public class FileUtils {

    private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);

    private static final char[] FILENAME_SPECIAL_CHARS = {'"', '/', ':', '*', '?', '|', '<', '>', '!', '\\', ';'};
    private static final char[] FILENAME_POSIX_FORBIDDEN_CHARS = {'/', '\0'};
    private static final Pattern FILENAME_SPECIAL_CHARS_PATTERN = getSpecialCharsPattern();
    private static final Pattern FILENAME_POSIX_FORBIDDEN_CHARS_PATTERN = getPosixForbiddenCharsPattern();
    public static final Pattern FILENAME_WIN_RESERVED = Pattern.compile("^(?i:(CON|PRN|AUX|NUL|CLOCK\\$|COM[1-9]|LPT[1-9]))(\\..*)?$");
    public static final String FILE_PREFIX_TEMP = "eldoc_tmp_";


    //
    // Class methods ------------------------------------------------------------------------------
    //
    /**
     * Creates copy of the file represented as InputStream in system temp directory
     *
     * @param inputStream of the source file
     * @param fileExtension file extension to be used (to be provided with dot, like ".docx"), or null for default extension ".tmp"
     * @return file copy in system temp directory
     * @throws IOException
     */
    public static Path createTempFileCopy(InputStream inputStream, String fileExtension) throws IOException {
        final Path tempFile = createTempFile(fileExtension);
        Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }


    /**
     * Creates temp file in system temp directory
     *
     * @param fileExtension file extension to be used (to be provided with dot, like ".docx"), or null for default extension ".tmp"
     * @return file in system temp directory
     * @throws IOException
     */
    public static Path createTempFile(String fileExtension) throws IOException {
        return Files.createTempFile(FILE_PREFIX_TEMP, fileExtension);
    }


    /**
     * Checks whether File is valid by given absolute path by testing whether it: exists & is file & can be read & size more than 0
     *
     * @param fileToCheck
     * @return
     */
    public static boolean isFileValid(String fileToCheck) {
        if (fileToCheck == null || fileToCheck.isBlank()) {
            return false;
        }
        return isFileValid(Paths.get(fileToCheck));
    }


    /**
     * Checks whether File is valid by given absolute path by testing whether it: exists & is file & can be read & size more than 0
     *
     * @param fileToCheck
     * @return
     */
    public static boolean isFileValid(Path fileToCheck) {
        if (fileToCheck == null) {
            return false;
        }

        try {
            return (Files.exists(fileToCheck) && Files.isRegularFile(fileToCheck) && Files.isReadable(fileToCheck) && Files.size(fileToCheck) > 0);
        } catch (IOException e) {
            LOG.warn("Error checking file validity: {}", fileToCheck, e);
        }

        return false;
    }


    /**
     * Checks whether Folder is valid by given absolute path by testing whether it: exists
     *
     * @param folderToCheck
     * @return
     */
    public static boolean isFolderValid(String folderToCheck) {
        if (folderToCheck == null || folderToCheck.isBlank()) {
            return false;
        }
        return isFolderValid(Paths.get(folderToCheck));
    }


    /**
     * Checks whether Folder is valid by given absolute path by testing whether it: exists
     *
     * @param folderToCheck
     * @return
     */
    public static boolean isFolderValid(Path folderToCheck) {
        if (folderToCheck == null) {
            return false;
        }

        try {
            return Files.isDirectory(folderToCheck);
        } catch (FileSystemNotFoundException | SecurityException | IllegalArgumentException e) {
            LOG.warn("Error checking folder validity: {}", folderToCheck, e);
        }
        return false;
    }


    /**
     * Quietly deletes Files or Directories by the given path
     *
     * @param path
     */
    public static boolean deleteQuietly(Path path) {
        try {
            if (Files.isDirectory(path)) {
                // If it's a directory, recursively delete contents first
                AtomicBoolean deleteSuccess = new AtomicBoolean(true);  // Initially assume success
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (!deleteQuietlyInner(file)) {
                            deleteSuccess.set(false);  // Mark failure
                            return FileVisitResult.TERMINATE;  // Stop on failure
                        }
                        return FileVisitResult.CONTINUE;
                    }


                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        if (exc != null) {
                            LOG.debug("Error visiting directory: {}", dir, exc);
                            deleteSuccess.set(false);  // Mark failure due to exception
                            return FileVisitResult.TERMINATE;
                        }
                        if (!deleteQuietlyInner(dir)) {
                            deleteSuccess.set(false);  // Mark failure
                            return FileVisitResult.TERMINATE;  // Stop on failure
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                return deleteSuccess.get();  // Return success or failure
            } else {
                // If it's a file, delete it directly
                return deleteQuietlyInner(path);  // Direct file deletion
            }
        } catch (IOException e) {
            LOG.debug("Error occurred trying to delete path: {}", path, e);
            return false;  // Catch any exceptions and mark failure
        }
    }


    private static boolean deleteQuietlyInner(Path path) {
        try {
            return Files.deleteIfExists(path);  // Delete the file or directory if it exists
        } catch (IOException e) {
            LOG.debug("Error occurred trying to delete path: {}", path, e);
            return false;  // Failure to delete, return false
        }
    }


    /**
     * Generates random string
     *
     * @param length
     * @return
     */
    public static String generateRandomString(int length) {
        String charSet = "1234567890abcdefghijklmnopqrstuvwxyz";
        char[] result = new char[length];
        Random rnd = new Random();
        for (int i = 0; i < length; i++) {
            result[i] = charSet.charAt(rnd.nextInt(charSet.length()));
        }
        return String.valueOf(result);
    }


    /**
     * Method for testing objects for serialization
     *
     * @param object
     * @throws IOException
     */
    public void serializeObject(Object object) throws IOException {
        Path tempSerFile = createTempFile("ser");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(tempSerFile));) {
            out.writeObject(object);
            LOG.info("Object of Class[{}] has been serialized to: {}", object.getClass().getName(), tempSerFile);
        } catch (IOException ex) {
            LOG.error("Serialization error of the object of type: " + object.getClass().getName(), ex);
        }
    }


    /**
     * Method checks if provided content type is valid for the provided fileName, and returns valid contentType
     * for provided file by its name
     *
     * @param contentType Submitted content type, that needs to be validated
     * @param filePath Filename or full file-path with filename
     * @return Valid content type based on the file name, as defined by {@link #getContentType(java.lang.String) }
     */
    public static String ensureContentType(String contentType, String filePath) {
        String detectedContentType = getContentType(filePath);
        if (isBlank(contentType)) {
            LOG.debug("File[{}]: contentType not provided, assigning detected contentType: [{}]", filePath, detectedContentType);
        } else if (!Objects.equals(contentType, detectedContentType)) {
            LOG.debug("File[{}]: provided contentType [{}] does not match the detected contentType [{}]. The detected contentType will be used instead", filePath, contentType, detectedContentType);
        }
        return detectedContentType;
    }


    /**
     * Detects content-type based on the file name otherwise returns default content type {@link MimeType#DEFAULT}
     *
     * @param filePath filename or full file-path with filename
     * @return
     */
    public static String getContentType(String filePath) {
        if (isBlank(filePath)) {
            return MimeType.DEFAULT;
        }
        return getContentType(Paths.get(filePath));
    }


    public static String getContentType(Path filePath) {
        if (filePath == null) {
            return MimeType.DEFAULT;
        }

        String contentType = null;
        try {
            contentType = MimeType.getMimeType(FilenameUtils.getExtension(filePath.toString()));
        } catch (Exception ex) {
            LOG.error("Error determining contentType for the path: {}", filePath, ex);
        }

        if (isBlank(contentType)) {
            LOG.warn("Cannot determine contentType for the path: '{}', using default contentType '{}' instead", filePath, MimeType.DEFAULT);
            contentType = MimeType.DEFAULT;
        }

        return contentType;
    }


    /**
     * Returns file name and size label suitable for UI
     *
     * @param fileName
     * @param fileSize
     * @return
     */
    public static String getFileDownloadName(String fileName, long fileSize) {
        return String.format("%s (%s)", fileName, bytesToDisplayString(fileSize));
    }


    /**
     * Returns size in human-friendly string format
     *
     * @param fileSize
     * @return
     */
    public static String bytesToDisplayString(long fileSize) {
        String[] aSizes = {"Bytes", "KB", "MB", "GB", "TB", "PB"};
        if (fileSize > 0) {
            double calcnr = Math.floor(Math.log(fileSize) / Math.log(1024));
            DecimalFormat format = new DecimalFormat("0.## " + aSizes[(int) calcnr]);
            format.setRoundingMode(RoundingMode.UP);
            return format.format(fileSize / Math.pow(1024, Math.floor(calcnr)));
        } else {
            return String.format("0 %s", aSizes[0]);
        }
    }


    /**
     * Cleanups special characters from the given string (representing filename) and trims it
     *
     * @param fileName
     * @return cleared and trimmed string
     */
    public static String cleanSpecialCharsForFileName(String fileName) {
        return cleanSpecialCharsForFileName(fileName, "");
    }


    /**
     * Cleanups special characters from the given string (representing filename) and trims it
     *
     * @param fileName
     * @param replacement String, that will be used as a replacement for special characters
     * @return cleared and trimmed string
     */
    public static String cleanSpecialCharsForFileName(String fileName, String replacement) {
        return FILENAME_SPECIAL_CHARS_PATTERN.matcher(fileName).replaceAll(replacement).trim();
    }


    /**
     * Cleanups special characters from the given string (representing filename) and trims it
     *
     * @param fileName
     * @param replacement String, that will be used as a replacement for special characters
     * @return cleared and trimmed string
     */
    public static String cleanForbiddenPosixCharsForFileName(String fileName, String replacement) {
        return FILENAME_POSIX_FORBIDDEN_CHARS_PATTERN.matcher(fileName).replaceAll(replacement).trim();
    }


    /**
     * Detect special characters in string
     *
     * @param fileName
     * @return cleared and trimmed string
     */
    public static boolean containsSpecialChars(String fileName) {
        return FILENAME_SPECIAL_CHARS_PATTERN.matcher(fileName).find();
    }


    /**
     * Validates File/Folder name for both *nix and Windows file systems
     *
     * @param name
     * @return {@code true} if {@code name} is a legal file or directory name
     */
    public static boolean isNameValid(String name) {
        // 1. Generic syntax check
        if (name == null || name.isBlank()) {
            return false;
        }

        try {
            Paths.get(name);
        } // catches NUL char and a few other invalid patterns
        catch (InvalidPathException ex) {
            return false;
        }

        // TODO: Consider adding lenght check less than ~250 in length
        // 2. OS‑specific rules
        return isValidWindows(name) && isValidPosix(name);
    }


    private static boolean isValidWindows(String name) {
        // Reserved device names (CON, COM1, …) – allow extension after dot
        if (FILENAME_WIN_RESERVED.matcher(name).matches()) {
            return false;
        }

        // Forbidden characters
        if (containsSpecialChars(name)) {
            return false;
        }

        // Names may not end with space or dot
        char last = name.charAt(name.length() - 1);
        return last != ' ' && last != '.';
    }


    private static boolean isValidPosix(String name) {
        // Only slash and NUL are forbidden on POSIX
        return !name.contains("/") && name.indexOf('\0') < 0;
    }


    /**
     * Extra check: does it fit on the *actual* filesystem
     *
     * @param parent
     * @param name
     * @return
     */
    public static boolean fitsOnFileSystem(Path parent, String name) {
        // length limit – 255 bytes for most Unix FS, 260 chars win32 legacy
        int max = 255;          // tune if you need exact FS query
        if (name.getBytes().length > max) {
            return false;
        }

        // Add more checks (existing files, forbidden patterns, etc.) here
        return true;
    }


    /**
     * Return special characters pattern
     *
     * @return cleared and trimmed string
     */
    private static Pattern getSpecialCharsPattern() {
        final StringBuilder sb = new StringBuilder();
        for (char specialChar : FILENAME_SPECIAL_CHARS) {
            sb.append(String.valueOf(specialChar));
        }
        return Pattern.compile("[" + Pattern.quote(sb.toString()) + "]");
    }


    /**
     * Return special characters pattern
     *
     * @return cleared and trimmed string
     */
    private static Pattern getPosixForbiddenCharsPattern() {
        final StringBuilder sb = new StringBuilder();
        for (char specialChar : FILENAME_POSIX_FORBIDDEN_CHARS) {
            sb.append(String.valueOf(specialChar));
        }
        return Pattern.compile("[" + Pattern.quote(sb.toString()) + "]");
    }


    /**
     * Splits file path into separate elements and returns them as list<br>
     * For the path Folder1/file2.txt returns List["Folder1", "file2.txt"]<br>
     *
     * @param path
     * @return
     */
    public static List<String> pathToList(String path) {
        // TODO: Further upgrade to this method may be required by properly differentiating path and file name
        // using methods: FilenameUtils.getName and FilenameUtils.getPath
        return Stream.of(path.trim().split("\\\\|/"))
                .filter(pathItem -> pathItem != null && !pathItem.isBlank())
                .map(pathItem -> pathItem.trim())
                .collect(Collectors.toList());
    }
}
