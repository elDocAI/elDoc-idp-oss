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

import de.flapdoodle.commons.reverse.StateID;
import de.flapdoodle.commons.reverse.transitions.Start;
import de.flapdoodle.embed.mongo.commands.MongodArguments;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.Storage;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.types.DatabaseDir;
import de.flapdoodle.embed.process.config.store.Package;
import de.flapdoodle.embed.process.types.ProcessArguments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddedMongoFactoryTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsOnlyRhelAndRocky10OnLinuxX64IncludingMinorReleases() throws Exception {
        Path release = tempDir.resolve("os-release");
        for (String distro : new String[] {"rocky", "rhel"}) {
            for (String version : new String[] {"10", "10.1"}) {
                Files.writeString(release, "ID=" + distro + "\nVERSION_ID=\"" + version + "\"\n");
                assertTrue(EmbeddedMongoFactory.isRhelOrRockyLinux10("Linux", "amd64", release));
                assertTrue(EmbeddedMongoFactory.isRhelOrRockyLinux10("Linux", "x86_64", release));
                assertFalse(EmbeddedMongoFactory.isRhelOrRockyLinux10("Linux", "aarch64", release));
                assertFalse(EmbeddedMongoFactory.isRhelOrRockyLinux10("Windows", "amd64", release));
            }
            Files.writeString(release, "ID=" + distro + "\nVERSION_ID=9.6\n");
            assertFalse(EmbeddedMongoFactory.isRhelOrRockyLinux10("Linux", "amd64", release));
        }
        Files.writeString(release, "ID=ubuntu\nVERSION_ID=10\n");
        assertFalse(EmbeddedMongoFactory.isRhelOrRockyLinux10("Linux", "amd64", release));
        assertFalse(EmbeddedMongoFactory.isRhelOrRockyLinux10("Linux", "amd64", tempDir.resolve("missing")));
    }

    @Test
    void buildsStartupArgumentsForPinnedVersionWithoutDownloading() {
        for (boolean rhelOrRocky : new boolean[] {false, true}) {
            for (String configuredVersion : new String[] {"8.2", "8.2.12"}) {
                var mongod = Mongod.builder().from(EmbeddedMongoFactory.create(rhelOrRocky))
                        .net(Start.to(Net.class).initializedWith(Net.of("127.0.0.1", 27018, false)))
                        .databaseDir(Start.to(DatabaseDir.class).initializedWith(DatabaseDir.of(tempDir)))
                        .mongodArguments(Start.to(MongodArguments.class).initializedWith(
                                MongodArguments.builder().replication(Storage.of("eldoc-rs", 0))
                                        .useNoJournal(false).build()))
                        .build();
                try (var state = mongod.transitions(EmbeddedMongoService.resolveVersion(configuredVersion))
                        .walker().initState(StateID.of(ProcessArguments.class))) {
                    var args = state.current().value();
                    assertTrue(args.contains("--replSet"));
                    assertTrue(args.contains("eldoc-rs"));
                    assertTrue(args.contains("--dbpath"));
                    assertTrue(args.contains(tempDir.toString()));
                    assertFalse(args.contains("--smallfiles"));
                    assertFalse(args.contains("--noprealloc"));
                    assertFalse(args.contains("--nojournal"));
                }
            }
        }
    }

    @Test
    void resolvesPinnedVersionWithDefaultOsResolverWithoutDownloading() {
        try (var state = EmbeddedMongoFactory.create(false)
                .transitions(EmbeddedMongoService.resolveVersion("8.2")).walker()
                .initState(StateID.of(Package.class))) {
            assertTrue(state.current().url().contains("8.2.12"));
        }
    }

    @Test
    void resolvesRhelCompatibleArchiveUsingRequestedVersionWithoutDownloading() {
        for (String version : new String[] {"8.2", "8.0.23"}) {
            var resolved = EmbeddedMongoService.resolveVersion(version);
            if ("8.2".equals(version)) {
                assertEquals("8.2.12", resolved.asInDownloadPath());
            }
            try (var state = EmbeddedMongoFactory.create(true).transitions(resolved).walker()
                    .initState(StateID.of(Package.class))) {
                assertEquals("https://fastdl.mongodb.org/linux/mongodb-linux-x86_64-rhel93-"
                        + resolved.asInDownloadPath() + ".tgz", state.current().url());
            }
        }
    }
}
