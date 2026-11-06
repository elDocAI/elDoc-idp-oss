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

import ai.eldoc.util.JsonbUtils;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.FacesConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * String representation of Date Converter
 */
@FacesConverter(value = "dateToIsoStringConverter")
public class DateToIsoStringConverter implements Converter<String> {

    private static final Logger LOG = LoggerFactory.getLogger(DateToIsoStringConverter.class);


    @Override
    public String getAsString(FacesContext context, UIComponent component, String modelValue) {
        if (isBlank(modelValue)) {
            return "";
        }
        TimeZone timeZone = TimeZone.getTimeZone((String) component.getAttributes().get("timeZone"));
        String pattern = (String) component.getAttributes().get("pattern");
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setTimeZone(timeZone);

        Date date = JsonbUtils.getInstance().fromJson(modelValue, Date.class);
        return sdf.format(date);
    }


    @Override
    public String getAsObject(FacesContext context, UIComponent component, String uiSubmittedValue) {
        if (isBlank(uiSubmittedValue)) {
            return null;
        }
        TimeZone timeZone = TimeZone.getTimeZone((String) component.getAttributes().get("timeZone"));
        String pattern = (String) component.getAttributes().get("pattern");
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setTimeZone(timeZone);
        try {
            Date date = sdf.parse(uiSubmittedValue);
            return JsonbUtils.getInstance().toJson(date);
        } catch (ParseException ex) {
            LOG.error("Error converting date", ex);
            return null;
        }
    }
}
