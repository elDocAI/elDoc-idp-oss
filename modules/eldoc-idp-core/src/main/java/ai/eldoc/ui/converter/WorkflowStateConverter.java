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
import ai.eldoc.model.Workflow;
import ai.eldoc.util.JsfUtils;
import ai.eldoc.util.LabelUtils;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.FacesConverter;
import java.util.Locale;
import static ai.eldoc.workflow.WorkflowUtils.getProcessNameLabelId;
import static ai.eldoc.workflow.WorkflowUtils.getProcessNameLabelIdReco;


/**
 * Workflow State Converter
 */
@FacesConverter("workflowStateConverter")
public class WorkflowStateConverter implements Converter<Integer> {

    @Override
    public String getAsString(FacesContext context, UIComponent component, Integer stateId) {
        if (stateId == null) {
            return "";
        }
        if (component.getAttributes().get("workflow") instanceof Workflow workflow) {
            return getLabelByState(stateId, workflow, JsfUtils.getLocale(context));
        }
        return "";
    }


    @Override
    public Integer getAsObject(FacesContext context, UIComponent component, String submittedValue) {
        throw new ElDocRuntimeException("Not supported for read-only property");
    }


    public static String getLabelByState(Integer stateId, Workflow workflow, Locale locale) {
        if (workflow == null || stateId == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        if (workflow.isRecoWF()) {
            sb.append(LabelUtils.getLabel(getProcessNameLabelIdReco(stateId, workflow.getStep()), locale));
        } else {
            sb.append(LabelUtils.getLabel(getProcessNameLabelId(stateId), locale));
        }

        return sb.toString();
    }
}
