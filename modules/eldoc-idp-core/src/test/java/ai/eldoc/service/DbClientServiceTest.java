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

import com.mongodb.MongoClientSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class DbClientServiceTest {

    @Test
    void leavesClientAnonymousWhenCredentialsAreBlank() {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();

        DbClientService.applyCredential(builder, AppConfigService.IDP_OSS_DEFAULT_TENANT_NAME, "eldoc_idposs", "", new char[0]);

        assertNull(builder.build().getCredential());
    }


    @Test
    void appliesCredentialWhenUsernameAndPasswordAreConfigured() {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();

        DbClientService.applyCredential(builder, AppConfigService.IDP_OSS_DEFAULT_TENANT_NAME, "auth-db", "db-user", "secret".toCharArray());

        assertNotNull(builder.build().getCredential());
        assertEquals("db-user", builder.build().getCredential().getUserName());
        assertEquals("auth-db", builder.build().getCredential().getSource());
    }


    @Test
    void rejectsPartialCredentials() {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();

        assertThrows(IllegalArgumentException.class,
                () -> DbClientService.applyCredential(builder, AppConfigService.IDP_OSS_DEFAULT_TENANT_NAME, "auth-db", "db-user", new char[0]));
        assertThrows(IllegalArgumentException.class,
                () -> DbClientService.applyCredential(builder, AppConfigService.IDP_OSS_DEFAULT_TENANT_NAME, "auth-db", "", "secret".toCharArray()));
    }
}