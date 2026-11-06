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
package ai.eldoc.context;

import ai.eldoc.model.PropertyAwareModel;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import java.io.Serial;
import java.io.Serializable;


/**
 * User UI Settings Context
 */
@SessionScoped
public class UserUiContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 3536525377168160165L;

    public static final String UI_MODULE_IDP = "idp";

    private static final String PROP_WORKFLOW_STATUS_VIEW_NAME = "__prop_wrkfViewName";

    @Inject
    private UserContext _userContext;

    private boolean _hideMainMenu;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void mainMenuShow() {
        _hideMainMenu = false;
    }


    public void mainMenuHide() {
        _hideMainMenu = true;
    }


    public void mainMenuToggle() {
        _hideMainMenu = !_hideMainMenu;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public boolean isHideMainMenu() {
        return _hideMainMenu;
    }


    public String getWorkflowsPageViewName() {
        return PropertyAwareModel.getProperty(_userContext.getSessionSettings(), PROP_WORKFLOW_STATUS_VIEW_NAME, null, String.class);
    }


    public void setWorkflowsPageViewName(String viewName) {
        if (viewName == null) {
            _userContext.getSessionSettings().remove(PROP_WORKFLOW_STATUS_VIEW_NAME);
        } else {
            _userContext.getSessionSettings().put(PROP_WORKFLOW_STATUS_VIEW_NAME, viewName);
        }
    }
}
