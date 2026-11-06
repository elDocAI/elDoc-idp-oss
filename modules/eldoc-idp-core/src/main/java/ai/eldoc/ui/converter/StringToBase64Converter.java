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
import java.nio.charset.StandardCharsets;
import java.util.Base64;


/**
 * String To Base64 Converter
 */
@FacesConverter(value = "stringToBase64Converter")
public class StringToBase64Converter implements Converter {

    @Override
    public Object getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value
                .replaceAll("[\\u0020\\u00A0\\u2000-\\u200B\\u202F\\u205F\\u3000]", " ") // Replace all other whitespace except \t and \n with a regular space
                .trim();

        // Where:
        // The regex pattern "[\\u0020\\u00A0\\u2000-\\u200B\\u202F\\u205F\\u3000]" matches all Unicode whitespace characters except tabs (\t) and newlines (\n):
        // \u0020: Regular space
        // \u00A0: Non-breaking space
        // \u2000-\u200B: Various Unicode spaces (e.g., en space, em space, thin space, zero-width space)
        // \u202F: Narrow no-break space
        // \u205F: Medium mathematical space
        // \u3000: Ideographic space

        if (trimmedValue.isBlank()) {
            return null;
        }

        return Base64.getEncoder().withoutPadding().encodeToString(trimmedValue.getBytes(StandardCharsets.UTF_8));
    }


    @Override
    public String getAsString(FacesContext context, UIComponent component, Object value) {
        return value != null ? new String(Base64.getDecoder().decode(value.toString()), StandardCharsets.UTF_8) : null;
    }

}
