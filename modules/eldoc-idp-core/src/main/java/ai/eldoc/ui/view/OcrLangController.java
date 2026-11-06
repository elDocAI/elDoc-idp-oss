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
package ai.eldoc.ui.view;

import ai.eldoc.ocr.OcrLang;
import ai.eldoc.ocr.OcrService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.commons.collections4.CollectionUtils;
import java.util.List;
import java.util.stream.Collectors;
import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;


/**
 * OCR Lang Controller
 */
@Named
@ApplicationScoped
public class OcrLangController {

    //
    // Methods ------------------------------------------------------------------------------------
    //
    public List<String> completeLangCode(String query) {
        return getOcrLangs().stream()
                .filter(ocrLang -> containsIgnoreCase(ocrLang.getCode(), query) || containsIgnoreCase(ocrLang.getName(), query) || containsIgnoreCase(ocrLang.getNameNative(), query))
                .map(ocrLang -> ocrLang.getCode())
                .collect(Collectors.toList());
    }


    public String getOcrLangUiLabelByCode(String langCode) {
        return OcrService.getOcrLanguageByCode(langCode).getNameUI();
    }


    public List<String> normilizeLangs(List<String> langs) {
        if (CollectionUtils.isEmpty(langs)) {
            return List.of(OcrService.getDefaultLanguageCode());
        }
        return langs;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns OCR Languages available for the current OCR Engine (enabled languages only are returned)
     *
     * @return
     */
    public List<OcrLang> getOcrLangs() {
        return OcrService.getAvailableLanguages();
    }

}
