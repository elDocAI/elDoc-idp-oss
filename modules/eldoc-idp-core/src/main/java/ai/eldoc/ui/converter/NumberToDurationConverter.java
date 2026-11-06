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

import ai.eldoc.util.JsfUtils;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.*;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Converter for converting integer value of days into milliseconds (for storing as Date) and vice versa
 */
@FacesConverter(value = "numberToDurationConverter")
public class NumberToDurationConverter implements Converter<Date> {

    @Override
    public Date getAsObject(FacesContext context, UIComponent component, String value) {
        if (isBlank(value) || value.trim().equals("0")) {
            return null;
        }
        try {
            return new Date(TimeUnit.DAYS.toMillis(Integer.parseInt(value.trim())));
        } catch (Exception e) {
            FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_ERROR, JsfUtils.getLabel(context, "msg.error.general"), e.toString());
            throw new ConverterException(msg, e);
        }
    }


    @Override
    public String getAsString(FacesContext context, UIComponent component, Date value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(TimeUnit.MILLISECONDS.toDays(value.getTime()));
    }

}
