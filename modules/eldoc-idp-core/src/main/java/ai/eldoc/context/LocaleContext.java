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
package ai.eldoc.context;

import ai.eldoc.util.I18n;
import ai.eldoc.util.LabelUtils;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.faces.annotation.RequestMap;
import jakarta.faces.context.ExternalContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;


/**
 * Locale Context Class
 */
@Named
@SessionScoped
public class LocaleContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 4342143443178033874L;

    @Inject
    private ExternalContext _externalContext;

    @Inject
    @RequestMap
    private Map<String, Object> _requestMap;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private UserContext _userContext;

    private Locale _locale = I18n.LOCALE_ENG; // Default locale


    @PostConstruct
    void init() {
        if (_externalContext != null) {

            Set<String> langSysCodesToUse;
            if (_userContext.isUserTypeInternal()) {
                // For internal users - we use only locales, enabled in their tenant
                langSysCodesToUse = _configContext.getLangsEnabled();
            } else {
                // For the rest of the users (External / not authenticated (anonymous) users) - we allow using any supported locales
                langSysCodesToUse = _configContext.getLangsDefault();
            }

            List<Locale> systemLocalesToUse = langSysCodesToUse
                    .stream()
                    .map(langSysCode -> I18n.getLocaleByLangSysCode(langSysCode))
                    .toList();

            // Try to select most preffered lang by browser
            _locale = getClosestSystemLocaleOrDefault(systemLocalesToUse, _externalContext.getRequestLocales(), I18n.LOCALE_ENG);
        }
    }


    /**
     * Sets locale to the requested one
     *
     * @param langSysCode
     */
    public void setLocale(String langSysCode) {
        boolean allowLocaleSet;
        if (_userContext.isUserTypeInternal()) {
            // For internal users we check presence of the requested langSysCode in per-Tenant settings
            allowLocaleSet = _configContext.getLangsEnabled().contains(langSysCode);
        } else {
            // For the rest of the world users (external/anonymous) - we check in the default system languages
            allowLocaleSet = _configContext.getLangsDefault().contains(langSysCode);
        }

        if (!allowLocaleSet) {
            return; // Unknown locale requested
        }
        _locale = I18n.getLocaleByLangSysCode(langSysCode);
    }


    public Locale getLocale() {
        if (_locale != null && !_requestMap.containsKey("__localeLangCheck")) {

            // Define which system languages codes to check
            Set<String> supportedLangSysCodesSet;
            if (_userContext.isUserTypeInternal()) {
                // For internal users - use langSysCode in per-Tenant settings
                supportedLangSysCodesSet = _configContext.getLangsEnabled();
            } else {
                // For the rest of the world users (external/anonymous) - use default system languages
                supportedLangSysCodesSet = _configContext.getLangsDefault();
            }

            // Check if we need to reset locale (in case user logins with uk language into tenant without uk language)
            if (!supportedLangSysCodesSet.contains(I18n.getLangSysCodeByLocale(_locale))) {
                _locale = I18n.getLocaleByLangSysCode(_configContext.getDefaultLang());
            }

            // Cache this check so we have it once per request
            _requestMap.put("__localeLangCheck", true);
        }
        return _locale;
    }


    public String getLocaleLanguageTag() {
        return getLocale().toLanguageTag();
    }


    /**
     * Returns Lang System Code based on currently used by user Locale e.g.: 'en', 'uk', 'zht', etc.
     *
     * @return
     */
    public String getLangSystemCode() {
        return I18n.getLangSysCodeByLocale(getLocale());
    }


    public String getLabel(String labelId) {
        return LabelUtils.getLabel(labelId, getLocale());
    }


    public String getLabel(String labelId, String langsysCode) {
        return LabelUtils.getLabel(labelId, langsysCode);
    }


    public String getLabel(String labelId, Locale locale) {
        return LabelUtils.getLabel(labelId, locale);
    }


    public static LocaleContext getInstance() {
        return CDI.current().select(LocaleContext.class).get();
    }


    //
    // Private Methods ----------------------------------------------------------------------------
    //
    /**
     * Checks if requested by browser locales present in the list of supported locales and returns supportedLocale (considering priorities)
     *
     * @param availableLocale
     * @param requestLocales
     * @return
     */
    private static Locale getClosestSystemLocaleOrDefault(List<Locale> supportedLocales, Iterator<Locale> requestLocales, Locale defaultLocale) {
        while (requestLocales.hasNext()) {
            Locale requestedNormilizedLocaleLang = I18n.getNormalizedLocale(requestLocales.next());

            for (Locale availableLocale : supportedLocales) {
                // Exact match (language + country)
                if (requestedNormilizedLocaleLang.equals(availableLocale)) {
                    return availableLocale;
                }
            }

            for (Locale availableLocale : supportedLocales) {
                // Match only language if one of the locales lacks a country code
                if (requestedNormilizedLocaleLang.getLanguage().equals(availableLocale.getLanguage())) {
                    return availableLocale;
                }
            }
        }

        return defaultLocale; // No match found
    }
}
