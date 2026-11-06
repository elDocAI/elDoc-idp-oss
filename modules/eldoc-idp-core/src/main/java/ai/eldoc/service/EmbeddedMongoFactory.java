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

package ai.eldoc.service;
import de.flapdoodle.commons.reverse.transitions.Start;
import de.flapdoodle.embed.mongo.transitions.ImmutablePackageOfCommandDistribution;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.packageresolver.PlatformPackageResolver;
import de.flapdoodle.embed.mongo.types.DistributionBaseUrl;
import de.flapdoodle.embed.process.config.store.*;
import de.flapdoodle.embed.process.config.store.Package;
import de.flapdoodle.embed.process.distribution.ArchiveType;
import de.flapdoodle.embed.process.distribution.Distribution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Embedded Mongo Factory adds support for RHEL / Rocky Linux 10
 */
public class EmbeddedMongoFactory {

    private static final Path OS_RELEASE = Path.of("/etc/os-release");


    private EmbeddedMongoFactory() {
        // Utility class.
    }


    /**
     * Creates the Mongod configuration for the current OS.
     *
     * @return configured Flapdoodle Mongod instance
     */
    public static Mongod create() {
        return create(isRhelOrRockyLinux10(System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""), OS_RELEASE));
    }

    static Mongod create(boolean rhelOrRockyLinux10) {
        if (!rhelOrRockyLinux10) {
            return Mongod.builder()
                    .packageOfDistribution(ImmutablePackageOfCommandDistribution.withDefaults()
                            .withCommandPackageResolver(command -> distribution -> {
                                PlatformPackageResolver resolver = new PlatformPackageResolver(command);
                                if (!"8.2.12".equals(distribution.version().asInDownloadPath())) {
                                    return resolver.packageFor(distribution);
                                }
                                // Use the known platform layout for this release line with the newer patch.
                                Package knownPackage = resolver.packageFor(
                                        Distribution.of(Version.V8_2_9, distribution.platform()));
                                return Package.builder()
                                        .archiveType(knownPackage.archiveType())
                                        .fileSet(knownPackage.fileSet())
                                        .hint(knownPackage.hint())
                                        .url(knownPackage.url().replace("8.2.9", "8.2.12"))
                                        .build();
                            }))
                    .build();
        }

        return Mongod.builder()
                .packageOfDistribution(ImmutablePackageOfCommandDistribution.withDefaults()
                        .withCommandPackageResolver(command ->
                                distribution ->
                                        Package.builder().archiveType(ArchiveType.TGZ)
                                                .fileSet(FileSet.builder()
                                                        .addEntry(FileType.Executable, "mongod", ".*/bin/mongod").build())
                                                .url("https://fastdl.mongodb.org/linux/mongodb-linux-x86_64-rhel93-"
                                                        + distribution.version().asInDownloadPath() + ".tgz")
                                                .build()))
                .distributionBaseUrl(Start.to(DistributionBaseUrl.class).initializedWith(DistributionBaseUrl.of("")))
                .build();
    }


    /**
     * Detects RHEL or Rocky Linux 10 on x86_64.
     */
    static boolean isRhelOrRockyLinux10(String osName, String arch, Path osRelease) {

        if (!"Linux".equalsIgnoreCase(osName)) {
            return false;
        }

        if (!"amd64".equalsIgnoreCase(arch) && !"x86_64".equalsIgnoreCase(arch)) {
            return false;
        }

        if (!Files.isRegularFile(osRelease)) {
            return false;
        }

        try {

            Map<String, String> values = Files.readAllLines(osRelease).stream().filter(line -> !line.isBlank() && !line.startsWith("#") && line.contains("=")).map(line -> line.split("=", 2)).collect(Collectors.toMap(parts -> parts[0], parts -> parts[1].replace("\"", ""), (a, b) -> b));

            return ("rocky".equalsIgnoreCase(values.get("ID")) || "rhel".equalsIgnoreCase(values.get("ID")))
                    && values.getOrDefault("VERSION_ID", "").matches("10(?:\\.\\d+)*");

        } catch (IOException e) {
            throw new IllegalStateException("Unable to detect Linux distribution", e);
        }
    }
}
