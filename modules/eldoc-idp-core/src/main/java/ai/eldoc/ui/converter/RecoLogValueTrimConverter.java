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
 * RecoLog value trim and format converter
 */
@FacesConverter(value = "recoLogValueTrimConverter")
public class RecoLogValueTrimConverter implements Converter<String> {

    @Override
    public String getAsObject(FacesContext context, UIComponent component, String value) {
        return value;
    }


    @Override
    public String getAsString(FacesContext context, UIComponent component, String value) {
        if (value == null) {
            return "";
        }
        String[] stt = value.split("\\r\\n");
        StringBuilder sb = new StringBuilder();

        for (int rowNumber = 0; rowNumber < stt.length; rowNumber++) {
            sb.append(stt[rowNumber].replaceAll("\\t", "<br/>"));
            if (rowNumber >= 15) {
                sb.append("<br/>...");
                break;
            }
        }
        String result = sb.toString();
        if(result.length() >= 303) {
            result = result.substring(0, 300).concat("...");
        }
        return result;
    }

}
