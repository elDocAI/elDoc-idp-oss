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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


class AppConfigServiceDiscoveryTest {

    private static final String CONFIG_FILENAME = "eldoc-idp-oss.conf";

    @TempDir
    Path tempDirectory;


    @Test
    void discoversConfigFromWorkingDirectory() throws IOException {
        Path configDirectory = createConfigDirectory(tempDirectory);

        assertEquals(configDirectory.toString(), resolve(null, null, tempDirectory));
    }


    @Test
    void discoversConfigFromAncestorOfModuleWorkingDirectory() throws IOException {
        Path configDirectory = createConfigDirectory(tempDirectory);
        Path moduleDirectory = Files.createDirectories(tempDirectory.resolve("modules/eldoc-idp-core"));

        assertEquals(configDirectory.toString(), resolve(null, null, moduleDirectory));
    }


    @Test
    void selectsNearestAncestorConfig() throws IOException {
        Path outerConfigDirectory = createConfigDirectory(tempDirectory);
        Path nestedWorkingDirectory = Files.createDirectories(tempDirectory.resolve("modules"));
        Path nearestConfigDirectory = createConfigDirectory(nestedWorkingDirectory);

        assertEquals(nearestConfigDirectory.toString(), resolve(null, null, nestedWorkingDirectory));
        assertEquals(outerConfigDirectory.toString(), resolve(null, null, tempDirectory));
    }


    @Test
    void returnsNullWhenNoConfigFileIsFound() throws IOException {
        Files.createDirectories(tempDirectory.resolve("configs"));

        assertNull(resolve(null, null, tempDirectory));
    }


    @Test
    void ignoresConfigCandidateThatIsNotARegularFile() throws IOException {
        Files.createDirectories(tempDirectory.resolve("configs").resolve(CONFIG_FILENAME));

        assertNull(resolve(null, null, tempDirectory));
    }


    @Test
    void prefersJvmOptionToEnvironmentVariable() throws IOException {
        Path jvmConfigDirectory = createConfigDirectory(tempDirectory.resolve("jvm"));
        Path environmentConfigDirectory = createConfigDirectory(tempDirectory.resolve("environment"));

        assertEquals(jvmConfigDirectory.toString(), resolve(jvmConfigDirectory.toString(), environmentConfigDirectory.toString(), tempDirectory));
    }


    @Test
    void usesEnvironmentVariableWhenJvmOptionIsAbsent() throws IOException {
        Path environmentConfigDirectory = createConfigDirectory(tempDirectory.resolve("environment"));

        assertEquals(environmentConfigDirectory.toString(), resolve(null, environmentConfigDirectory.toString(), tempDirectory));
    }


    @Test
    void blankJvmOptionTriggersDiscoveryWithoutUsingEnvironmentVariable() throws IOException {
        Path discoveredConfigDirectory = createConfigDirectory(tempDirectory);
        Path environmentConfigDirectory = createConfigDirectory(tempDirectory.resolve("environment"));

        assertEquals(discoveredConfigDirectory.toString(), resolve(" ", environmentConfigDirectory.toString(), tempDirectory));
    }


    @Test
    void blankEnvironmentVariableTriggersDiscovery() throws IOException {
        Path discoveredConfigDirectory = createConfigDirectory(tempDirectory);

        assertEquals(discoveredConfigDirectory.toString(), resolve(null, " ", tempDirectory));
    }


    @Test
    void doesNotReplaceExplicitMissingDirectoryWithDiscoveredConfig() throws IOException {
        createConfigDirectory(tempDirectory);
        String explicitDirectory = tempDirectory.resolve("missing-config").toString();

        assertEquals(explicitDirectory, resolve(explicitDirectory, null, tempDirectory));
    }


    private static Path createConfigDirectory(Path root) throws IOException {
        Path configDirectory = Files.createDirectories(root.resolve("configs"));
        Files.writeString(configDirectory.resolve(CONFIG_FILENAME), "tenantId=test");
        return configDirectory;
    }


    private static String resolve(String jvmOption, String environmentVariable, Path workingDirectory) {
        return AppConfigService.resolveConfigDirectory(jvmOption, environmentVariable, workingDirectory);
    }
}
