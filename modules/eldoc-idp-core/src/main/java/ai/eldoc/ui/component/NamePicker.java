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
import jakarta.faces.component.*;


/**
 * Name Picker Backing Component
 */
@FacesComponent("namePicker")
public class NamePicker extends UIInput implements NamingContainer {

    public static final String BEAN_NAME = "uiNamePicker";
    public static final String COMPONENT_RENDERED = "uiNamePicker_rendered";

    public static final String ATTR_VALUE = "value";
    public static final String ATTR_BINDTO = "bindTo";
    public static final String ATTR_OBJECT_BEAN = "objectBean";
    public static final String ATTR_EXCLUDE_NAMES = "excludeNames";
    public static final String ATTR_GROUPS = "groups";
    public static final String ATTR_BPM_NAMES = "bpmNames";
    public static final String ATTR_SINGLE_NAME = "singleName";
    public static final String ATTR_REFRESH_ID = "refreshId";
    public static final String ATTR_DISABLED = "disabled";


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void initNamePicker() {
        NamePickerBean.getInstance().initPicker(this);
    }


    private void enablePickerContentRendering() {
        JsfUtils.getViewMap().put(COMPONENT_RENDERED, true);
    }


    //
    // Getters/setters ----------------------------------------------------------------------------
    //
    /**
     * Returns the component family of {@link UINamingContainer}. (that's just required by the composite component)
     *
     * @return
     */
    @Override
    public String getFamily() {
        enablePickerContentRendering();
        return UINamingContainer.COMPONENT_FAMILY;
    }
}
