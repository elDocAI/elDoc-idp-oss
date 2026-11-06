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
package ai.eldoc.ui.component;

import ai.eldoc.util.JsfUtils;
import jakarta.el.ELContext;
import jakarta.el.ValueExpression;
import jakarta.faces.component.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;


/**
 * crmOrg Picker Backing Component
 */
@FacesComponent("refWFPicker")
public class RefWFPicker extends UIInput implements NamingContainer {

    private static final Logger LOG = LoggerFactory.getLogger(RefWFPicker.class);

    public static final String BEAN_NAME = "uiRefWFPicker";
    public static final String COMPONENT_RENDERED = "uiRefWFPicker_rendered";

    public static final String ATTR_VALUE = "value";
    public static final String ATTR_REFRESH_ID = "refreshId";
    public static final String ATTR_DISABLED = "disabled";
    public static final String ATTR_SINGLE_SELECT = "singleSelect";


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void initRefDocsPicker() {
        RefWFPickerBean.getInstance().initPicker(this);
    }


    private void enablePickerContentRendering() {
        JsfUtils.getViewMap().put(COMPONENT_RENDERED, true);
    }


    public void clear() {
        ValueExpression valueExpression = getValueExpression(ATTR_VALUE);
        if (valueExpression == null) {
            LOG.warn("No value expression is bound to component attribute '{}'", ATTR_VALUE);
            return;
        }

        ELContext elContext = JsfUtils.getFacesContext().getELContext();
        Class<?> targetType = valueExpression.getType(elContext);

        Object value = resolveEmptyValue(targetType);
        valueExpression.setValue(elContext, value);
    }


    private Object resolveEmptyValue(Class<?> targetType) {
        if (targetType == null) {
            LOG.warn("Unable to resolve target data type; clearing value with null");
            return null;
        }

        if (String.class.isAssignableFrom(targetType)) {
            return null;
        } else if (Set.class.isAssignableFrom(targetType)) {
            return new LinkedHashSet<>();
        } else if (List.class.isAssignableFrom(targetType)) {
            return new ArrayList<>();
        } else {
            LOG.warn("Unexpected target data type: {}; clearing value with null", targetType);
            return null;
        }
    }


    //
    // Getters/setters ----------------------------------------------------------------------------
    //


    /**
     * Returns the component family of {@link UINamingContainer}. (that's just required by composite component)
     *
     * @return
     */
    @Override
    public String getFamily() {
        enablePickerContentRendering();
        return UINamingContainer.COMPONENT_FAMILY;
    }

}
