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

import ai.eldoc.service.AppConfigService.EmbeddedMongoConfig;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Explicit integration test. Run with {@code -Dtest=EmbeddedMongoServiceIT}; it may download MongoDB.
 */
class EmbeddedMongoServiceIT {

    @TempDir
    Path _tempDir;


    @Test
    void persistsDataAcrossReplicaSetRestartAndReleasesProcess() throws Exception {
        EmbeddedMongoConfig config = config();
        EmbeddedMongoService firstLaunch = new EmbeddedMongoService();
        String firstConnection;
        try {
            firstLaunch.start(config);
            firstConnection = firstLaunch.getConnectionString();
            try (MongoClient client = MongoClients.create(firstConnection)) {
                client.getDatabase("embedded_mongo_it").getCollection("markers")
                        .insertOne(new Document("value", "persisted"));
            }
        } finally {
            firstLaunch.destroy();
        }

        EmbeddedMongoService secondLaunch = new EmbeddedMongoService();
        try {
            secondLaunch.start(config);
            assertNotEquals(firstConnection, secondLaunch.getConnectionString());
            try (MongoClient client = MongoClients.create(secondLaunch.getConnectionString())) {
                assertEquals(1L, client.getDatabase("embedded_mongo_it").getCollection("markers").countDocuments());
            }
        } finally {
            secondLaunch.destroy();
        }
    }


    @Test
    void reusesAlreadyRunningProcessWithoutTakingOwnership() throws Exception {
        EmbeddedMongoConfig config = config();
        EmbeddedMongoService firstLaunch = new EmbeddedMongoService();
        EmbeddedMongoService secondLaunch = new EmbeddedMongoService();
        try {
            firstLaunch.start(config);
            String firstConnection = firstLaunch.getConnectionString();

            secondLaunch.start(config);

            assertEquals(firstConnection, secondLaunch.getConnectionString());
            secondLaunch.destroy();
            try (MongoClient client = MongoClients.create(firstConnection)) {
                assertEquals(1L, client.getDatabase("admin").runCommand(new Document("hello", 1)).get("ok", Number.class).longValue());
            }
        } finally {
            secondLaunch.destroy();
            firstLaunch.destroy();
        }
    }


    @Test
    void discoversRunningProcessWhenEndpointMetadataIsMissing() throws Exception {
        EmbeddedMongoConfig config = config();
        EmbeddedMongoService firstLaunch = new EmbeddedMongoService();
        EmbeddedMongoService secondLaunch = new EmbeddedMongoService();
        try {
            firstLaunch.start(config);
            String firstConnection = firstLaunch.getConnectionString();
            Path dataDir = config.getDataDir();
            Files.delete(dataDir.resolveSibling(dataDir.getFileName() + ".eldoc-mongod.properties"));

            secondLaunch.start(config);

            assertEquals(firstConnection, secondLaunch.getConnectionString());
        } finally {
            secondLaunch.destroy();
            firstLaunch.destroy();
        }
    }


    @Test
    void refusesProcessOutsideConfiguredBinaryCache() throws Exception {
        EmbeddedMongoConfig config = config();
        EmbeddedMongoService launch = new EmbeddedMongoService();
        try {
            launch.start(config);
            int port = new com.mongodb.ConnectionString(launch.getConnectionString()).getHosts().stream()
                    .mapToInt(host -> Integer.parseInt(host.substring(host.lastIndexOf(':') + 1)))
                    .findFirst().orElseThrow();
            EmbeddedMongoService.Endpoint endpoint = new EmbeddedMongoService.Endpoint(config.getHost(), port);
            assertTrue(EmbeddedMongoService.isExpectedProcess(endpoint, config));

            PropertiesConfiguration properties = new PropertiesConfiguration();
            properties.setProperty(EmbeddedMongoConfig.PARAM_DATA_DIR, config.getDataDir().toString());
            properties.setProperty(EmbeddedMongoConfig.PARAM_BINARY_CACHE_DIR, _tempDir.resolve("other-bin").toString());
            properties.setProperty(EmbeddedMongoConfig.PARAM_REPLICA_SET, config.getReplicaSet());
            EmbeddedMongoConfig otherCache = EmbeddedMongoConfig.create(properties);
            assertFalse(EmbeddedMongoService.isExpectedProcess(endpoint, otherCache));
        } finally {
            launch.destroy();
        }
    }


    private EmbeddedMongoConfig config() {
        PropertiesConfiguration properties = new PropertiesConfiguration();
        properties.setProperty(EmbeddedMongoConfig.PARAM_DATA_DIR, _tempDir.resolve("data").toString());
        properties.setProperty(EmbeddedMongoConfig.PARAM_BINARY_CACHE_DIR, _tempDir.resolve("bin").toString());
        properties.setProperty(EmbeddedMongoConfig.PARAM_REPLICA_SET, "integration-rs");
        return EmbeddedMongoConfig.create(properties);
    }
}