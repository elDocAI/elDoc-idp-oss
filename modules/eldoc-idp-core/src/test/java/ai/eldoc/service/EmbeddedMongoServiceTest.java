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

import de.flapdoodle.embed.mongo.distribution.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


class EmbeddedMongoServiceTest {

    @TempDir
    Path _tempDir;


    @Test
    void preparesPersistentDirectoriesWithoutDeletingExistingData() throws Exception {
        Path dataDir = _tempDir.resolve("data");
        Files.createDirectories(dataDir);
        Path marker = Files.writeString(dataDir.resolve("marker"), "persisted");

        EmbeddedMongoService.prepareDirectory(dataDir, "data");

        assertEquals("persisted", Files.readString(marker));
    }


    @Test
    void resolvesMongoVersionLinesAndExactVersions() {
        assertSame(Version.Main.V8_0, EmbeddedMongoService.resolveVersion("8.0"));
        assertSame(Version.Main.V8_1, EmbeddedMongoService.resolveVersion("8.1"));
        assertEquals("8.2.12", EmbeddedMongoService.resolveVersion("8.2").asInDownloadPath());
        assertEquals("8.2.12", EmbeddedMongoService.resolveVersion("8.2.12").asInDownloadPath());
        assertEquals("8.2.9", EmbeddedMongoService.resolveVersion("8.2.9").asInDownloadPath());
        assertEquals("8.0.23", EmbeddedMongoService.resolveVersion("8.0.23").asInDownloadPath());
        assertThrows(IllegalArgumentException.class, () -> EmbeddedMongoService.resolveVersion("9.9"));
    }


    @Test
    void buildsReplicaSetConnectionString() {
        assertEquals("mongodb://127.0.0.1:27018/?replicaSet=test-rs",
                EmbeddedMongoService.replicaSetConnectionString("127.0.0.1", 27018, "test-rs"));
        assertEquals("mongodb://[::1]:27018/?replicaSet=test-rs",
                EmbeddedMongoService.replicaSetConnectionString("::1", 27018, "test-rs"));
    }
}
