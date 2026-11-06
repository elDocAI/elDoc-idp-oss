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

import ai.eldoc.model.directory.*;
import ai.eldoc.service.DirectoryService;
import ai.eldoc.util.JsfUtils;
import ai.eldoc.util.LabelUtils;
import jakarta.faces.component.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Locale;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * NameItem Component
 */
@FacesComponent("nameItem")
public class NameItem extends UIOutput implements NamingContainer {

    private static final Logger LOG = LoggerFactory.getLogger(NameItem.class);

    // local request scope cache
    private final HashMap<String, DirectoryItem> _cache = new HashMap<>();


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


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns relative URL-path to the given DirectoryItem
     *
     * @param di
     * @return
     */
    public String getItemUrl(DirectoryItem di) {
        return getDirectoryService().getItemUrl(di);
    }


    /**
     * Returns relative URL-path to the avatar image of the given DirectoryItem
     *
     * @param di
     * @return
     */
    public String getItemPicUrl(DirectoryItem di) {
        return getDirectoryService().getItemPicUrl(di);
    }


    /**
     * Return specified attribute value or otherwise the specified default if it's null.
     */
    private <T> T getAttributeValue(String key, T defaultValue) {
        T value = (T) getAttributes().get(key);
        return (value != null) ? value : defaultValue;
    }


    private DirectoryService getDirectoryService() {
        return DirectoryService.getInstance();
    }


    /**
     * Return DirectoryService Item data (cached during request scope)<br>
     * Cache implementation: by systemId
     *
     * @return
     */
    public DirectoryItem getValueData() {
        Object value = getValue();
        try {
            String cacheId = null;
            if (value instanceof DirectoryItem dirItem) {
                cacheId = dirItem.getIdSys();
                if (!_cache.containsKey(dirItem.getIdSys())) {
                    _cache.put(cacheId, dirItem);
                }
            } else if (value instanceof String systemId) {
                cacheId = systemId;
                if (!_cache.containsKey(cacheId)) {
                    _cache.put(cacheId, getDirectoryService().getDirectoryItem(systemId));
                }
            }
            if (cacheId != null) {
                return _cache.get(cacheId);
            }
        } catch (Exception e) {
            LOG.error("Error performing lookup for nameItem: '{}'", value, e);
        }
        return getDirectoryService().getDirectoryItem(null);
    }


    public String getTitleUI() {
        return resolveTitleUI();
    }


    private String resolveTitleUI() {
        Locale locale = JsfUtils.getLocale(getFacesContext());
        if (getValueData() instanceof RoleNameItem roleNameItem) {
            return LabelUtils.getLabel("general.role", locale) + ": " + roleNameItem.getIdSys();
        } else if (getValueData() instanceof UndefinedNameItem) {
            if (isNotBlank(getValueData().getIdSys())) {
                return LabelUtils.getLabel("directoryItem.notFound", locale) + ": " + getValueData().getIdSys();
            } else {
                return LabelUtils.getLabel("directoryItem.notFound", locale);
            }
        }

        // For the remaining ones: SystemNameItem, BpmNameItem
        return getValueData().getItemNameUI();
    }

    /**
     * Return Directory Item data that could be cached during request scope<br>
     * Cache implementation: by clientId
     *
     * @return
     */
    /*
    public DirectoryItem getValueData() {
        if (_cache.get(getClientId()) == null) {
            Object value = getValue();
            if (value instanceof String) {
                LOG.log(Level.INFO, ">> value is string: " + String.valueOf(value));
                _cache.put(getClientId(), DirectoryService.getInstance().getDirectoryItem(String.valueOf(value)));
            } else {
                LOG.log(Level.INFO, ">> value is DI: " + ((DirectoryItem) value).getIdSys());
                _cache.put(getClientId(), (DirectoryItem) value);
            }
        }
        return _cache.get(getClientId());
    }
     */
}
