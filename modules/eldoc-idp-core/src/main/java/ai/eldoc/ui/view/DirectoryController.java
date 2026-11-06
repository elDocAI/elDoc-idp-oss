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

import ai.eldoc.model.directory.DirectoryItem;
import ai.eldoc.model.directory.SystemNameItem;
import ai.eldoc.service.DirectoryService;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.ViewScoped;
import java.io.Serial;
import java.io.Serializable;


/**
 * Bean for working with User Directory
 */
@Named
@ViewScoped
public class DirectoryController extends AbstractUIController implements Serializable {

    @Serial
    private static final long serialVersionUID = 3582795065055236386L;

    @Inject
    private DirectoryService _directoryService;

    private String _id;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    public DirectoryItem getDirectoryItem(String directoryName) {
        return _directoryService.getDirectoryItem(directoryName);
    }


    public DirectoryItem getDirectoryItem(String directoryName, DirectoryItem dItem) {
        if (dItem != null) {
            return dItem;
        } else {
            return _directoryService.getDirectoryItem(directoryName);
        }
    }


    /**
     * Checks if the given name is a SystemUser account
     *
     * @param systemId
     * @return
     */
    public boolean isSystemUser(String systemId) {
        return SystemNameItem.isSystemName(systemId);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public void setId(String id) {
        _id = id;
    }


    public String getId() {
        return _id;
    }


    public String getItemPicUrl(DirectoryItem di) {
        return _directoryService.getItemPicUrl(di);
    }


    public DirectoryController getInstance() {
        return CDI.current().select(DirectoryController.class).get();
    }
}
