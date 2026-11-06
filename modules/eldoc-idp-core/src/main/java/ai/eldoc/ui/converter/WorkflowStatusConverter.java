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
import ai.eldoc.util.JsfUtils;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.FacesConverter;
import static ai.eldoc.workflow.WorkflowUtils.getProcessStatusLabelId;


/**
 * Workflow Status Converter
 */
@FacesConverter("workflowStatusConverter")
public class WorkflowStatusConverter implements Converter<Integer> {

    @Override
    public String getAsString(FacesContext context, UIComponent component, Integer statusId) {
        if (statusId == null) {
            return "";
        }
        return JsfUtils.getLabel(context, getProcessStatusLabelId(statusId));
    }


    @Override
    public Integer getAsObject(FacesContext context, UIComponent component, String submittedValue) {
        throw new ElDocRuntimeException("Not supported for read-only property");
    }
}
