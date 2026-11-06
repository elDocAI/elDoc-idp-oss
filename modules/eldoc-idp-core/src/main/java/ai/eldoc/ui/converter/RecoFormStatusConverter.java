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
import java.util.Locale;
import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.model.reco.RecoForm;
import ai.eldoc.util.JsfUtils;
import ai.eldoc.util.LabelUtils;


@FacesConverter(value = "recoFormStatusConverter")
public class RecoFormStatusConverter implements Converter<Integer> {


    @Override
    public Integer getAsObject(FacesContext context, UIComponent component, String value) {
        if (null == value) {
            throw new ElDocRuntimeException("Not implemented for value: " + value);
        } else {
            return getStatusByLabel(value);
        }
    }


    @Override
    public String getAsString(FacesContext context, UIComponent component, Integer value) {
        return getLabelByStatus(value, JsfUtils.getLocale(context));
    }


    public static int getStatusByLabel(String label) {
        switch (label) {
            case "NEW":
                return RecoForm.NEW;
            case "ACTIVE":
                return RecoForm.ACTIVE;
            case "SUSPENDED":
                return RecoForm.SUSPENDED;
            default:
                throw new ElDocRuntimeException("Not implemented for value: " + label);
        }
    }


    public static String getLabelByStatus(int status, Locale locale) {
        switch (status) {
            case RecoForm.NEW:
                return LabelUtils.getLabel("recoForm.status.new", locale);
            case RecoForm.ACTIVE:
                return LabelUtils.getLabel("recoForm.status.active", locale);
            case RecoForm.SUSPENDED:
                return LabelUtils.getLabel("recoForm.status.suspended", locale);
            default:
                return "N/A(" + status + ")";
        }
    }

}
