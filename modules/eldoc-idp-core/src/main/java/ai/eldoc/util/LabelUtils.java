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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;


/**
 * Project Labels Utils
 */
public final class LabelUtils {

    private static final Logger LOG = LoggerFactory.getLogger(LabelUtils.class);

    private static final String LABELS_STRINGS = "labels";


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private LabelUtils() {

    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Gets the bundle resource by given name
     *
     * @param bundleName
     * @param locale
     * @return ResourceBundle
     */
    private static ResourceBundle getResourceBundle(String bundleName, Locale locale) {
        try {
            if (locale != null) {
                return ResourceBundle.getBundle(bundleName, locale);
            } else {
                return ResourceBundle.getBundle(bundleName);
            }
        } catch (Exception e) {
            LOG.error("Error getting ResourceBundle '{}' for locale '{}'", bundleName, locale, e);
        }
        return null;
    }


    /**
     * Gets label string by given key from the standard labels resource bundle<br>
     * Method uses en_US locale
     *
     * @param labelKey
     * @return
     */
    public static String getLabel(String labelKey) {
        return getLabel(labelKey, I18n.LOCALE_ENG);
    }


    /**
     * Gets label string by given key from the standard labels resource bundle<br>
     * Locale is queried from the known locales based on the provided lang code
     *
     * @param labelKey
     * @param langSysCode
     * @return
     */
    public static String getLabel(String labelKey, String langSysCode) {
        return getLabel(labelKey, I18n.getLocaleByLangSysCode(langSysCode));
    }


    /**
     * Gets label string by given key and locale from the standard labels resource bundle<br>
     *
     * @param labelKey
     * @param locale
     * @return
     */
    public static String getLabel(String labelKey, Locale locale) {
        try {
            return getResourceBundle(LABELS_STRINGS, locale).getString(labelKey);
        } catch (MissingResourceException e2) {
            LOG.warn("Error getting label '{}' for locale '{}'", labelKey, locale);
        } catch (Exception e1) {
            LOG.warn("Error getting label '{}' for locale '{}'", labelKey, locale, e1);
        }
        return labelKey != null ? "???" + labelKey + "???" : "-label-error-";
    }

}
