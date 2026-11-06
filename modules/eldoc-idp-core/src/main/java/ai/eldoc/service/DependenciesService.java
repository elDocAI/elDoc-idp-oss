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
import ai.eldoc.ocr.TessDataProvisioner;
import ai.eldoc.service.AppConfigService.IdpConfig;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Function;

/**
 * Dependencies Service
 */
@Startup
@Singleton
@DependsOn("InitAndUpgradeService")
@TransactionManagement(TransactionManagementType.BEAN)
public class DependenciesService {

    private static final Logger LOG = LoggerFactory.getLogger(DependenciesService.class);

    @Inject
    private AppConfigService _appConfigService;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @PostConstruct
    void init() {
        Path fallbackPath = Path.of(System.getProperty("java.io.tmpdir"), TessDataProvisioner.DEFAULT_DIRECTORY_NAME);
        initialize(_appConfigService, new TessDataProvisioner(), fallbackPath);
    }


    static void initialize(AppConfigService appConfigService, TessDataProvisioner tessDataProvisioner, Path fallbackPath) {
        LOG.info("Starting dependency initialization");
        IdpConfig idpConfig = appConfigService.getIdpConfig();
        LOG.info("Checking whether Tesseract OCR is selected for the default or any tenant");
        if (!isTesseractSelected(idpConfig.getOcrType(null), appConfigService.getTenantIdSet(), idpConfig::getOcrType)) {
            LOG.info("Skipping Tesseract language-data provisioning because Tesseract OCR is not selected");
            return;
        }

        String configuredPath = idpConfig.getTessDataPath();
        LOG.info("Preparing Tesseract language data; configured path='{}', temporary fallback path='{}'", configuredPath, fallbackPath);
        try {
            Path tessDataPath = tessDataProvisioner.ensureTessData(configuredPath, fallbackPath);
            idpConfig.setRuntimeTessDataPath(tessDataPath.toString());
            LOG.info("Tesseract language data is ready at {}", tessDataPath);
            LOG.info("Dependency initialization completed");
        } catch (IOException ex) {
            LOG.error("Tesseract language-data provisioning failed", ex);
            throw new ElDocRuntimeException("Unable to download or prepare Tesseract language data", ex);
        }
    }


    static boolean isTesseractSelected(String defaultOcrType, Collection<String> tenantIds, Function<String, String> getOcrType) {
        if (isTesseract(defaultOcrType)) {
            return true;
        }
        return tenantIds.stream().anyMatch(tenantId -> isTesseract(getOcrType.apply(tenantId)));
    }


    private static boolean isTesseract(String ocrType) {
        return "tess".equalsIgnoreCase(ocrType == null ? "" : ocrType.trim());
    }

}
