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

import ai.eldoc.service.ListValueService;
import ai.eldoc.util.JsfUtils;
import jakarta.faces.component.*;
import jakarta.faces.context.FacesContext;
import java.io.IOException;


/**
 * ListValue Label backing component class
 */
@FacesComponent("lvLabel")
public class LvLabel extends UIOutput implements NamingContainer {

    private UIOutput _outputlabel;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns the component family of {@link UINamingContainer}. (that's just required by composite component)
     *
     * @return
     */
    @Override
    public String getFamily() {
        return UINamingContainer.COMPONENT_FAMILY;
    }


    /**
     * Set the available values
     *
     * @param context
     * @throws java.io.IOException
     */
    @Override
    public void encodeBegin(FacesContext context) throws IOException {
        String listValueId = (String) getAttributes().get("value");
        _outputlabel.setValue(ListValueService.getInstance().getLVData(listValueId, JsfUtils.getLocale(context)));
        super.encodeBegin(context);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public UIOutput getOutputLabel() {
        return _outputlabel;
    }


    public void setOutputLabel(UIOutput outputlabel) {
        _outputlabel = outputlabel;
    }
}
