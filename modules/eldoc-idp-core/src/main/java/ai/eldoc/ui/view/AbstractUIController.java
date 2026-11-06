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
package ai.eldoc.ui.view;

import jakarta.faces.annotation.SessionMap;
import jakarta.inject.Inject;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;


/**
 * Implements basic logic for working with models on UI
 */
public abstract class AbstractUIController implements Serializable {

    private static final long serialVersionUID = -7864103143638374072L;

    @Inject
    @SessionMap
    private Map<String, Object> _sessionScope;    
    
    private static final String DS_EDIT_MODE_FLAG = "__openInEditMode";
    private boolean _modeRead = true;


    /**
     * Decides on how to open document: in edit mode or read mode. Edit Mode used for newly created docs on redirect from Create page to Document page
     */
    protected boolean isOpenInEditMode(String workflowUrlId) {
        return Objects.equals(_sessionScope.remove(DS_EDIT_MODE_FLAG), workflowUrlId);
    }
    
    
    protected void setOpenInEditMode(String workflowUrlId) {
        _sessionScope.put(DS_EDIT_MODE_FLAG, workflowUrlId);
    }    
    
    
    public void setModeEdit() {
        _modeRead = false;
    }


    public void setModeRead() {
        _modeRead = true;
    }


    public boolean isModeEdit() {
        return !_modeRead;
    }


    public boolean isModeRead() {
        return _modeRead;
    }

}
