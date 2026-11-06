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

(function () {

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", apply);
    } else {
        apply();
    }

    function getNormalizedLocale() {
        const bodyLang = document.body?.getAttribute("lang");
        const raw = bodyLang || "en";
        return raw.trim().replace("-", "_");
    }

    function apply() {
        if (!window.PrimeFaces) {
            console.warn("PrimeFaces is not loaded, skipping localization overrides");
            return;
        }
        
        if (!PrimeFaces.locales) {
            console.warn("PrimeFaces.locales is not defined, skipping localization overrides");
            return;
        }

        const locale = getNormalizedLocale();

        if (locale === "zh_HK") {
            // elDoc uses zh_HK, but PrimeFaces does not have it, so we can use zh_TW as a fallback
            PrimeFaces.locales[locale] = PrimeFaces.locales["zh_TW"];
        }
    }

}) ();