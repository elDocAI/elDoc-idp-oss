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

import ai.eldoc.model.directory.DirectoryItem;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.FacesConverter;


/**
 * SystemId to NameItem Converter
 */
@FacesConverter(value = "directoryItemNameConverter")
public class DirectoryItemNameConverter implements Converter<DirectoryItem> {

    @Override
    public String getAsString(FacesContext context, UIComponent component, DirectoryItem dirItem) {
        if (dirItem == null) {
            return "";
        }
        return dirItem.getItemNameUI();
    }


    @Override
    public DirectoryItem getAsObject(FacesContext context, UIComponent component, String submittedValue) {
        throw new UnsupportedOperationException("Not implemented for the read-only item");
    }
}
