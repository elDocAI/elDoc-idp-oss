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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.model.DbFileRef;
import ai.eldoc.model.DbWebRef;
import ai.eldoc.util.DbWebRefUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import java.security.GeneralSecurityException;


/**
 * URL Service<br>
 * Provides support for web URL generation, encryption/decryption, signing, etc.
 */
@ApplicationScoped
public class UrlService {

    @Inject
    private AppConfigService _appConfigService;


    //
    // DbWebRef Methods ---------------------------------------------------------------------------
    //
    /**
     * Returns URL-path for accessing file attachment via web with encrypted DbWebRef param. Link is relative to the context path, meaning for MongoDB link will look like:<br>
     * <ul>
     * <li>/api/dataService/54gj63k45g645kl6584e31bd7bd02009c957f23d</li>
     * </ul>
     *
     * @param dbFileRef
     * @return
     */
    public String toFileHref(DbFileRef dbFileRef) {
        StringBuilder sb = new StringBuilder();
        sb.append("/api/dataService");
        sb.append("/");
        sb.append(toDbWebRefCipher(DbWebRefUtils.toDbWebRef(dbFileRef)));
        return sb.toString();
    }


    /**
     * Returns encrypted with Blowfish string representation of this DbWebRef (encoded in Base62)
     *
     * @param dbWebRef
     * @return
     */
    public String toDbWebRefCipher(DbWebRef dbWebRef) {
        try {
            return dbWebRef.toCipher(_appConfigService.getElDocConfig().getUrlCipherKey());
        } catch (GeneralSecurityException ex) {
            throw new ElDocRuntimeException(String.valueOf(dbWebRef), ex);
        }
    }


    /**
     * Constructs DbWebRef from the encrypted string representation of DbWebRef (Base62-encoded string of encrypted with Blowfish bytes)
     *
     * @param dbWebRefCipher
     * @return
     */
    public DbWebRef fromDbWebRefCipher(String dbWebRefCipher) {
        try {
            return DbWebRef.fromCipher(dbWebRefCipher, _appConfigService.getElDocConfig().getUrlCipherKey());
        } catch (GeneralSecurityException ex) {
            throw new ElDocRuntimeException(dbWebRefCipher, ex);
        }
    }


    public static UrlService getInstance() {
        return CDI.current().select(UrlService.class).get();
    }
}
