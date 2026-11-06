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
 * Converter combines two converters: StringNewLineRemovalConverter and StringTrimConverter
 */
@FacesConverter(value = "stringNewLineRemovalAndTrimConverter")
public class StringNewLineRemovalAndTrimConverter implements Converter<String> {

    private final Converter<String> _stringNewLineRemovalConverter = new StringNewLineRemovalConverter();
    private final Converter<String> _stringTrimConverter = new StringTrimConverter();


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public String getAsObject(FacesContext context, UIComponent component, String value) {
        String noNewlines = _stringNewLineRemovalConverter.getAsObject(context, component, value);
        return _stringTrimConverter.getAsObject(context, component, noNewlines);
    }


    @Override
    public String getAsString(FacesContext context, UIComponent component, String value) {
        String noNewlines = _stringNewLineRemovalConverter.getAsString(context, component, value);
        return _stringTrimConverter.getAsString(context, component, noNewlines);
    }
}
