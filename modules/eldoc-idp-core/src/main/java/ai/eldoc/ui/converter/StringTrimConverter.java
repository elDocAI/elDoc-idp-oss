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
package ai.eldoc.ui.converter;

import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.FacesConverter;


/**
 * Trims submitted value by removing leading and trailing spaces and also checks & removes characters like non‑breaking space, etc.<br>
 * IMPORTANT: Also replaces all non-standard spaces inside the input string with a standard ones
 *
 * @see https://en.wikipedia.org/wiki/Non-breaking_space
 */
@FacesConverter(value = "stringTrimConverter")
public class StringTrimConverter implements Converter<String> {

    private final static String NBSP = "\u00A0"; // NO-BREAK SPACE - char 160 (decimial)
    private final static String FIGURE_SPACE = "\u2007"; // FIGURE SPACE - char 8199 (decimial)
    private final static String NBSP_NARROW = "\u202F"; // NARROW NO-BREAK SPACE char 8239 (decimial)
    private final static String WORD_JOINER = "\u2060"; // WORD JOINER - char 8288 (decimial)    
    private final static String SPACE_REPLACE_REGEX = '[' + NBSP + FIGURE_SPACE + NBSP_NARROW + WORD_JOINER + ']';


    @Override
    public String getAsObject(FacesContext context, UIComponent component, String submittedValue) {
        if (submittedValue == null) {
            return null;
        }

        // We intentionally replace all kinds of spaces with the standard space
        String trimmedSubmittedValue = submittedValue.replaceAll(SPACE_REPLACE_REGEX, " ").trim();

        if (!trimmedSubmittedValue.isEmpty()) {
            return trimmedSubmittedValue;
        } else {
            return null;
        }
    }


    @Override
    public String getAsString(FacesContext context, UIComponent component, String value) {
        return value;
    }

}
