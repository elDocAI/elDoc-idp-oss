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
import ai.eldoc.service.AppConfigService.TenantConfig;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class AppConfigServiceEmbeddedMongoTest {

    @Test
    void usesTempBasedDefaultsForEmbeddedMongo() {
        EmbeddedMongoConfig config = EmbeddedMongoConfig.create(new PropertiesConfiguration());
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();

        assertEquals(tempDir.resolve("eldoc-idp-oss-db-data"), config.getDataDir());
        assertEquals(tempDir.resolve("eldoc-idp-oss-db-bin"), config.getBinaryCacheDir());
        assertEquals("127.0.0.1", config.getHost());
        assertEquals(0, config.getPort());
        assertEquals("8.2", config.getVersion());
        assertEquals("eldoc-rs", config.getReplicaSet());
    }


    @Test
    void resolvesConfiguredEmbeddedMongoSettings() {
        PropertiesConfiguration properties = new PropertiesConfiguration();
        properties.setProperty(EmbeddedMongoConfig.PARAM_DATA_DIR, "target/mongo-data");
        properties.setProperty(EmbeddedMongoConfig.PARAM_BINARY_CACHE_DIR, "target/mongo-bin");
        properties.setProperty(EmbeddedMongoConfig.PARAM_HOST, "localhost");
        properties.setProperty(EmbeddedMongoConfig.PARAM_PORT, "27018");
        properties.setProperty(EmbeddedMongoConfig.PARAM_VERSION, "8.0.16");
        properties.setProperty(EmbeddedMongoConfig.PARAM_REPLICA_SET, "test-rs");

        EmbeddedMongoConfig config = EmbeddedMongoConfig.create(properties);

        assertEquals(Path.of("target/mongo-data").toAbsolutePath().normalize(), config.getDataDir());
        assertEquals(Path.of("target/mongo-bin").toAbsolutePath().normalize(), config.getBinaryCacheDir());
        assertEquals("localhost", config.getHost());
        assertEquals(27018, config.getPort());
        assertEquals("8.0.16", config.getVersion());
        assertEquals("test-rs", config.getReplicaSet());
    }


    @Test
    void rejectsInvalidEmbeddedMongoPort() {
        PropertiesConfiguration properties = new PropertiesConfiguration();
        properties.setProperty(EmbeddedMongoConfig.PARAM_PORT, "65536");

        assertThrows(IllegalArgumentException.class, () -> EmbeddedMongoConfig.create(properties));
    }


    @Test
    void selectsOnlyTenantWithoutExternalConnectionForFallback() {
        TenantConfig fallback = tenant("fallback", "", "secret");
        TenantConfig secondFallback = tenant("second", "   ", "secret");
        TenantConfig external = tenant("external", "mongodb://db.example:27017", "secret");

        List<TenantConfig> selected = AppConfigService.tenantConfigsRequiringEmbeddedMongo(List.of(fallback, secondFallback, external));

        assertEquals(List.of(fallback, secondFallback), selected);
    }


    @Test
    void copiesTenantWhenInjectingConnectionAndPreservesOriginalAndSecrets() {
        TenantConfig original = tenant("fallback", "", "secret");

        TenantConfig replacement = original.withDBConnection("mongodb://127.0.0.1:27018");
        char[] returnedPassword = replacement.getDBPwd();
        returnedPassword[0] = 'X';

        assertEquals("", original.getDBConnStr());
        assertEquals("mongodb://127.0.0.1:27018", replacement.getDBConnStr());
        assertArrayEquals("secret".toCharArray(), replacement.getDBPwd());
    }


    @Test
    void uriAloneIsSufficientDatabaseConfiguration() {
        assertTrue(tenant("anonymous", "mongodb://127.0.0.1:27017", "").isDBParamsSet());
        assertFalse(tenant("missing", "", "secret").isDBParamsSet());
    }


    private static TenantConfig tenant(String tenantId, String connection, String password) {
        return new TenantConfig(tenantId, connection, null, null, "user", password, false,
                null, null, null, null, "*");
    }
}