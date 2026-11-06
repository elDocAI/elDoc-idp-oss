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
package ai.eldoc.util;

import java.util.*;


/**
 * Internalization (I18n) and Localization (L10n) Util Class<br><br>
 *
 * <strong>IMPORTANT:</strong> elDoc uses internal Language System Codes ('en', 'uk', 'es', 'ms', 'zht', 'zhs') for storing in DB,
 * and those cannot be used as standard languages codes (for cases like 'zhs'), as it is internal use only codes.<br><br>
 *
 * LangSystemCodes match the respective system supported Locales:<br>
 * - 'en' langSysCode gives Locale('en', '')<br>
 * - 'zht' langSysCode gives Locale('zh', 'HK')
 */
public class I18n {

    //
    // Language system codes
    //
    /**
     * English Language system code
     */
    public static final String LANG_SYSTEM_CODE_EN = "en";

    /**
     * Ukrainian Language system code
     */
    public static final String LANG_SYSTEM_CODE_UK = "uk";

    /**
     * Spanish Language system code
     */
    public static final String LANG_SYSTEM_CODE_ES = "es";

    /**
     * Malay Language system code
     */
    public static final String LANG_SYSTEM_CODE_MS = "ms";

    /**
     * Chinese Traditional Language system code
     */
    public static final String LANG_SYSTEM_CODE_ZHT = "zht";

    /**
     * Chinese Simplified Language system code
     */
    public static final String LANG_SYSTEM_CODE_ZHS = "zhs";

    /**
     * Defines all language system codes supported by the system.
     */
    public static final List<String> LANG_SYSTEM_CODES_ALL = Arrays.asList(
            LANG_SYSTEM_CODE_EN,
            LANG_SYSTEM_CODE_UK,
            LANG_SYSTEM_CODE_ES,
            LANG_SYSTEM_CODE_MS,
            LANG_SYSTEM_CODE_ZHT,
            LANG_SYSTEM_CODE_ZHS
    );

    //
    // Locales
    //
    /**
     * Locale for English
     */
    public static final Locale LOCALE_ENG = new Locale.Builder().setLanguage(LANG_SYSTEM_CODE_EN).build(); // Equals: Locale.ENGLISH

    /**
     * Locale for Ukrainian
     */
    public static final Locale LOCALE_UKR = new Locale.Builder().setLanguage(LANG_SYSTEM_CODE_UK).build();

    /**
     * Locale for Spanish
     */
    public static final Locale LOCALE_ES = new Locale.Builder().setLanguage(LANG_SYSTEM_CODE_ES).build();

    /**
     * Locale for Malay
     */
    public static final Locale LOCALE_MS = new Locale.Builder().setLanguage(LANG_SYSTEM_CODE_MS).build();

    /**
     * Locale for Chinese (Traditional) - Hong Kong
     */
    public static final Locale LOCALE_ZH_HK = new Locale.Builder().setLanguage("zh").setRegion("HK").build();

    /**
     * Locale for Chinese (Simplified) - Mainland China
     */
    public static final Locale LOCALE_ZH_CN = new Locale.Builder().setLanguage("zh").setRegion("CN").build();

    /**
     * Defines all supported system locales
     */
    private static final List<Locale> LOCALES_SYSTEM = List.of(
            LOCALE_ENG,
            LOCALE_UKR,
            LOCALE_ES,
            LOCALE_MS,
            LOCALE_ZH_HK,
            LOCALE_ZH_CN
    );

    /**
     * Defines normalization locale mapping for resolving closest system locale
     */
    private static final Map<Locale, Locale> LOCALES_NORMALIZATION_MAP = Map.of(
            new Locale("zh", "TW"), LOCALE_ZH_HK, // Chinese (Traditional) - Taiwan
            new Locale("zh", "MO"), LOCALE_ZH_HK, // Chinese (Simplified) - Macau
            new Locale("zh", "SG"), LOCALE_ZH_CN, // Chinese (Simplified) - Singapore
            new Locale("zh"), LOCALE_ZH_CN // Chinese - General
    );


    //
    // Constructors -------------------------------------------------------------------------------
    //
    private I18n() {
        // No public constructors allowed
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Helps to get closest system locale, as some locales require normalization, for example\
     * in case zh-TW is requested we can fallback to zh-HK (used in the system)
     *
     * @param locale requested locale, e.g. zh-TW
     * @return normalized locale which matches most closely used in the system, e.g. zh-HK for zh-TW
     */
    public static Locale getNormalizedLocale(Locale locale) {
        return LOCALES_NORMALIZATION_MAP.getOrDefault(locale, locale);
    }


    /**
     * Returns supported locale for the given language or default Locale.ENGLISH otherwise
     *
     * @param langSysCode
     * @return
     */
    public static Locale getLocaleByLangSysCode(String langSysCode) {
        if (LANG_SYSTEM_CODE_ZHT.equals(langSysCode)) {
            return LOCALE_ZH_HK; // Special handling for zht
        } else if (LANG_SYSTEM_CODE_ZHS.equals(langSysCode)) {
            return LOCALE_ZH_CN; // Special handling for zhs
        }

        return LOCALES_SYSTEM.stream()
                .filter(locale -> locale.getLanguage().equals(langSysCode))
                .findFirst()
                .orElse(LOCALE_ENG);
    }


    /**
     * Returns supported language tag for the given locale
     *
     * @param locale
     * @return
     */
    public static String getLangSysCodeByLocale(Locale locale) {
        if (LOCALE_ZH_HK.equals(locale)) {
            return LANG_SYSTEM_CODE_ZHT;
        } else if (LOCALE_ZH_CN.equals(locale)) {
            return LANG_SYSTEM_CODE_ZHS;
        }

        return locale.getLanguage(); // For the rest supported locales we use lang as langSysCode
    }


    /**
     * Returns localized field name in the model in the format like: itemName_lang<br>
     * Examples: "itemName_en" for 'en' lang, "itemName" for 'uk' lang, where itemName - base field name, and lang - language suffix<br>
     *
     * @param itemName field name which supports localization
     * @param lang language code like: uk, en
     * @return
     */
    public static String getL10nName(String itemName, String lang) {
        return itemName.concat(getL10nSuffix(lang));
    }


    /**
     * Returns localized field name suffix in the model in the format like: _lang<br>
     * Examples: "_en" for 'en' lang, "_uk" for 'uk' language an so on<br>
     *
     * @param lang language code like: uk, en
     * @return
     */
    public static String getL10nSuffix(String lang) {
        return "_".concat(lang);
    }
}
