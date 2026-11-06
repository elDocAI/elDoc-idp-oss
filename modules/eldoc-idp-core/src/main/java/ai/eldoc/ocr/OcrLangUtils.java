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
package ai.eldoc.ocr;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * OCR Language Utils Class
 */
public class OcrLangUtils {

    private static final Logger LOG = Logger.getLogger(OcrLangUtils.class.getName());

    private static final List<OcrLang> _langList = initLangs();


    //
    // Methods ------------------------------------------------------------------------------------
    //
    private static List<OcrLang> initLangs() {
        List<OcrLang> langs = new ArrayList<>();
        try {
            try (InputStream is = OcrLangUtils.class.getClassLoader().getResourceAsStream("idpOcrLangs.csv"); Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8); CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT);) {
                //0: engine, 1: code, 2: name, 3: nameNative, 4: enabled, 5: notes, 6: remark
                csvParser.forEach(csvRecord -> langs.add(
                        new OcrLang(csvRecord.get(0),
                                csvRecord.get(1),
                                csvRecord.get(2),
                                csvRecord.get(3),
                                Boolean.parseBoolean(csvRecord.get(4)))
                ));
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "OCR Languages initialization error", e);
        }
        return langs;
    }


    /**
     * Normalizes given langs list for the given OCR Engine
     *
     * @param engineId
     * @param languages
     * @return list of normalized languages for the requested ocrEngine. IMPORTANT: returned list can be empty, what should be handled by the engine itself
     */
    protected static List<String> normalizeLangList(String engineId, List<String> languages) {
        List<String> result = new ArrayList<>();

        languages.forEach(langCode -> {
            OcrLang ocrLang = getOcrLanguageByCodeOrNull(engineId, langCode);
            if (ocrLang != null) {
                // OcrLang is found by the langCode for the given engine, add it to the resulting list
                result.add(langCode);
            } else {
                // OcrLang was not found for the given engine by its langCode, try to locate the mapped language

                // Locate OcrLang by its langCode
                OcrLang ocrLangByCode = _langList.stream()
                        .filter(ocrLangItem -> Objects.equals(ocrLangItem.getCode(), langCode))
                        .findFirst()
                        .orElse(null);

                if (ocrLangByCode != null) {
                    // In case it was located - try to find a mapped language for the target ocrEngine using lang name as mapping
                    OcrLang ocrLangTarget = _langList.stream()
                            .filter(ocrLangItem -> Objects.equals(ocrLangItem.getEngine(), engineId) && Objects.equals(ocrLangItem.getName(), ocrLangByCode.getName()))
                            .findFirst()
                            .orElse(null);

                    if (ocrLangTarget != null) {
                        // In case mapped via name language was found for the target ocrEngine - add its code to the result list
                        result.add(ocrLangTarget.getCode());
                    }
                }
            }
        });

        return result;
    }


    /**
     * Sort languages list by language name
     *
     * @param langsList
     */
    protected static void sort(List<OcrLang> langsList) {
        langsList.sort(Comparator.comparing(OcrLang::getName));
    }


    /**
     * Returns list of all languages for the given OCR Engine type sorted by name
     *
     * @param ocrEngineName all languages are returned (as enabled as well as disabled)
     * @return
     */
    protected static List<OcrLang> getOcrLanguages(String ocrEngineName) {
        return _langList.stream()
                .filter(ocrLang -> Objects.equals(ocrLang.getEngine(), ocrEngineName))
                .sorted(Comparator.comparing(OcrLang::getName))
                .collect(Collectors.toList());
    }


    protected static OcrLang getOcrLanguageByCode(String engine, String code) {
        OcrLang ocrLang = getOcrLanguageByCodeOrNull(engine, code);
        if (ocrLang == null) {
            return OcrLang.initAsNotFound(engine, code);
        }
        return ocrLang;
    }


    private static OcrLang getOcrLanguageByCodeOrNull(String engine, String code) {
        return _langList.stream()
                .filter(ocrLang -> Objects.equals(ocrLang.getEngine(), engine) && Objects.equals(ocrLang.getCode(), code))
                .findFirst()
                .orElse(null);
    }

}
