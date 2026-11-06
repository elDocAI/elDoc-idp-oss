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

import ai.eldoc.model.ListValue;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.service.ListValueService;
import ai.eldoc.util.I18n;
import ai.eldoc.util.JsfUtils;
import jakarta.faces.component.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * ListValue Picker backing component class
 */
@FacesComponent("lvPicker")
public class LvPicker extends UIInput implements NamingContainer {

    private static final Logger LOG = LoggerFactory.getLogger(LvPicker.class);

    public static final String ATTR_KF_ID = "idKf"; // Foreign Key of the parent ListValue Directory to be used


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Completes ListValue search and implements ACL filter
     *
     * @param query
     * @return
     */
    public List<String> completeListValue(String query) {
        String idKf = getAttributeValue(getAttributes(), ATTR_KF_ID, null);
        if (isBlank(idKf)) {
            LOG.warn("'{}' is empty, this should not happen. Returning empty list as fall-back. Invoked by systemId[{}]", ATTR_KF_ID, SecurityUtils.getSubjectSystemId());
            return List.of();
        }

        return ListValueService.getInstance()
                .getAllByKF(idKf, query, I18n.getLangSysCodeByLocale(JsfUtils.getLocale(getFacesContext())), false, SecurityUtils.getSubjectAclContext())
                .stream()
                .map(ListValue::getId)
                .collect(Collectors.toList());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Return specified attribute value or otherwise the specified default if it's null.
     */
    private <T> T getAttributeValue(Map<String, Object> attributes, String key, T defaultValue) {
        T value = (T) attributes.get(key);
        return (value != null) ? value : defaultValue;
    }


    @Override
    public String getFamily() {
        return UINamingContainer.COMPONENT_FAMILY;
    }
}
