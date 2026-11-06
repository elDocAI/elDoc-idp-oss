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
import org.apache.commons.lang3.StringUtils;
import java.util.*;
import java.util.stream.Collectors;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * String to List Converter
 */
@FacesConverter(value = "stringToListConverter")
public class StringToListConverter implements Converter<List<String>> {

    @Override
    public List<String> getAsObject(FacesContext context, UIComponent component, String value) {
        if (!isBlank(value)) {
            return Arrays.stream(value.split(",|;")).filter(StringUtils::isNotBlank).map(String::trim).collect(Collectors.toList());
        }
        return new ArrayList();
    }


    @Override
    public String getAsString(FacesContext context, UIComponent component, List<String> value) {
        String keywords = "";
        if (value != null) {
            keywords = String.join(", ", value);
        }
        return keywords;
    }

}
