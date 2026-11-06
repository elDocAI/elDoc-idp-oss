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

import ai.eldoc.ElDocRuntimeException;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.FacesConverter;
import org.apache.commons.lang3.time.DurationFormatUtils;


/**
 */
@FacesConverter("durationFormatConverter")
public class DurationFormatConverter implements Converter<String> {

    @Override
    public String getAsObject(FacesContext context, UIComponent component, String value) {
        throw new ElDocRuntimeException("Not supported for read-only property");
    }


    @Override
    public String getAsString(FacesContext context, UIComponent component, String value) {
        Long valueLong = Long.valueOf(value);
        String format;
        if (valueLong > 60*60*24){
            format = "d'd' H'h' m'm' s's'";
        } else if (valueLong > 60*60){
            format = "H'h' m'm' s's'";
        } else if (valueLong > 60){
            format = "m'm' s's'";
        } else {
            format = "s's'";
        }
        return DurationFormatUtils.formatDuration(valueLong * 1000, format);
    }

}