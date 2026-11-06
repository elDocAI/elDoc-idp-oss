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
import ai.eldoc.service.DirectoryService;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.FacesConverter;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * SystemId to NameItem Converter
 */
@FacesConverter(value = "systemIdToDirectoryItemConverter")
public class SystemIdToDirectoryItemConverter implements Converter<DirectoryItem> {

    //@Inject
    private final DirectoryService _directoryService = DirectoryService.getInstance();


    @Override
    public String getAsString(FacesContext context, UIComponent component, DirectoryItem value) {
        if (value == null) {
            // returning an empty string in case of a null/empty model value is significant and required by the javadoc
            // http://docs.oracle.com/javaee/7/api/javax/faces/convert/Converter.html#getAsString-jakarta.faces.context.FacesContext-jakarta.faces.component.UIComponent-java.lang.Object-
            return "";
        }
        return value.getIdSys();
    }


    @Override
    public DirectoryItem getAsObject(FacesContext context, UIComponent component, String submittedValue) {
        if (isBlank(submittedValue)) {
            return null;
        }
        return _directoryService.getDirectoryItem(submittedValue);
    }
}
