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
package ai.eldoc.data;

import ai.eldoc.dao.BasicLazyDao;
import ai.eldoc.model.BasicModel;
import ai.eldoc.service.DirectoryService;
import org.bson.types.ObjectId;


/**
 * DirectoryService Lazy Data Model

 This class is required in order to provide handling for DirectoryService specific logic, like UI NamePicker which allows to switch directories while
 items from different directory or even BPM names are selected
 * @param <T>
 */
public class DirectoryLazyDataModel<T extends BasicModel> extends MongoLazyDataModel<T> {

    private static final long serialVersionUID = 2819070612233642890L;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public DirectoryLazyDataModel(BasicLazyDao<T> lazyDao) {
        super(lazyDao);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Retrieves Directory Item by its id. For DB based items it is ObjectId, for BMP items - it is systemId.
     * Provides handling for cases when selected Directory Items do not belong to the selected Directory (lazyDS) in the NamePicker on the UI
     * As otherwise when BPM name is selected and Users directory (lazyDS) is select as well (by default) - standard MongoLazyDataModel will throw Exception
     *
     * @param rowKey
     * @return
     */
    @Override
    public T getRowData(String rowKey) {
        // Check whether it is a valid ObjectId and if so - perform search in the DB
        if (ObjectId.isValid(rowKey)) {
            T di = super.getRowData(rowKey);
            if (di == null) {
                // In case DirItem was not found in the currently active DirectoryService - perform search in all available directories
                // This scenario may happen when Group Item was selected and then directory was switched to Users directory in the UI NamePicker
                di = (T) DirectoryService.getInstance().getDirectoryItemByDbId(rowKey);
            }
            return di;
        } else {
            return (T) DirectoryService.getInstance().getDirectoryItem(rowKey);
        }
    }
}
