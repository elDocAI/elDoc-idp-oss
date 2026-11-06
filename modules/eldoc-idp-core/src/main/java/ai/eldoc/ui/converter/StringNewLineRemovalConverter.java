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
 * Converter removes newLine characters from the given string
 */
@FacesConverter(value = "stringNewLineRemovalConverter")
public class StringNewLineRemovalConverter implements Converter<String> {

    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public String getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null) {
            return null;
        }
        // Remove all newline characters (\r or \n):
        return value.replaceAll("[\\r\\n]+", "");
    }


    @Override
    public String getAsString(FacesContext context, UIComponent component, String value) {
        // Typically just return the (already-cleaned) string
        // If you want to also remove newlines when displaying, you can do that here too.
        return value;
    }
}
