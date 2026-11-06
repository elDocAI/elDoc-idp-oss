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

import ai.eldoc.ocr.TessDataProvisioner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;


class DependenciesServiceTest {

    @TempDir
    Path _tempDir;


    @Test
    void initializesTessDataWhenTenantSelectsTesseractAndPublishesResolvedPath() throws Exception {
        URL archive = archive();
        TestConfiguration config = new TestConfiguration("vl", "tess", _tempDir.resolve("configured-tessdata").toString());

        DependenciesService.initialize(config, provisioner(archive), _tempDir.resolve("fallback"));

        assertEquals(_tempDir.resolve("configured-tessdata").toString(), config.idpConfig.getTessDataPath());
        assertEquals("model", Files.readString(_tempDir.resolve("configured-tessdata").resolve("eng.traineddata")));
    }


    @Test
    void initializesTessDataWhenDefaultOcrTypeIsTesseract() throws Exception {
        URL archive = archive();
        TestConfiguration config = new TestConfiguration("tess", "vl", _tempDir.resolve("configured-tessdata").toString());

        DependenciesService.initialize(config, provisioner(archive), _tempDir.resolve("fallback"));

        assertEquals(_tempDir.resolve("configured-tessdata").toString(), config.idpConfig.getTessDataPath());
    }


    @Test
    void skipsProvisioningWhenNoTenantUsesTesseract() throws Exception {
        TestConfiguration config = new TestConfiguration("vl", "vl", "");

        DependenciesService.initialize(config, provisioner(URI.create("http://127.0.0.1:1/unavailable.zip").toURL()), _tempDir.resolve("fallback"));

        assertEquals("", config.idpConfig.getTessDataPath());
        assertFalse(Files.exists(_tempDir.resolve("fallback")));
    }


    private URL archive() throws Exception {
        Path archive = _tempDir.resolve("tessdata.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new java.util.zip.ZipEntry("tessdata_best-4.1.0/eng.traineddata"));
            output.write("model".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return archive.toUri().toURL();
    }


    private static TessDataProvisioner provisioner(URL url) {
        return new TessDataProvisioner(url, TessDataProvisioner.PROGRESS_INTERVAL_BYTES, message -> {
        });
    }


    private static final class TestConfiguration extends AppConfigService {

        private final IdpConfig idpConfig;


        private TestConfiguration(String defaultOcrType, String tenantOcrType, String tessDataPath) {
            idpConfig = new IdpConfig() {
                private boolean _runtimePathSet;


                @Override
                public String getOcrType(String tenantId) {
                    return tenantId == null ? defaultOcrType : tenantOcrType;
                }


                @Override
                public String getTessDataPath() {
                    return _runtimePathSet ? super.getTessDataPath() : tessDataPath;
                }


                @Override
                void setRuntimeTessDataPath(String path) {
                    super.setRuntimeTessDataPath(path);
                    _runtimePathSet = true;
                }
            };
        }


        @Override
        public IdpConfig getIdpConfig() {
            return idpConfig;
        }


        @Override
        public Set<String> getTenantIdSet() {
            return Set.of("tenant");
        }
    }
}
