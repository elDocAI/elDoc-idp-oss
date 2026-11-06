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
package ai.eldoc.service;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.dao.ListValueDao;
import ai.eldoc.model.AclContext;
import ai.eldoc.model.ListValue;
import ai.eldoc.util.I18n;
import ai.eldoc.util.LabelUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * ListValues Service
 */
@ApplicationScoped
public class ListValueService {

    private static final Logger LOG = LoggerFactory.getLogger(ListValueService.class);

    @Inject
    private ListValueDao _listValueDao;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public ListValue getById(String id) {
        return _listValueDao.find(id);
    }


    /**
     * Returns ListValue records where foreign key is not set, in such case all 1st level ListValue records
     * are returned only
     *
     * @param aclData
     * @return
     */
    public List<ListValue> getAllFirstLevel(AclContext aclData) {
        return getAllByKF(null, aclData);
    }


    /**
     * Returns all active ListValues by the given Key Foreign ID
     *
     * @param idKf
     * @param aclData
     * @return
     */
    public List<ListValue> getAllByKF(String idKf, AclContext aclData) {
        return _listValueDao.findAllByKF(idKf, aclData);
    }


    /**
     * Returns all ListValues by the given Key Foreign ID and additional params
     *
     * @param idKf
     * @param searchFT
     * @param lang
     * @param showDeletedItems
     * @param aclData
     * @return
     */
    public List<ListValue> getAllByKF(String idKf, String searchFT, String lang, boolean showDeletedItems, AclContext aclData) {
        return _listValueDao.findAllByKF(idKf, searchFT, lang, showDeletedItems, aclData);
    }


    public List<ListValue> getAllByIdExtAndKF(String idExt, String idKf) {
        return _listValueDao.findAllByIdExtAndKF(idExt, idKf);
    }


    /**
     * Finds and returns ListValue data for the current locale
     *
     * @param listValueId
     * @param locale
     * @return
     */
    public String getLVData(String listValueId, Locale locale) {
        if (isBlank(listValueId)) {
            return null;
        }
        ListValue lv = _listValueDao.find(listValueId);
        if (lv != null) {
            return lv.getValue(I18n.getLangSysCodeByLocale(locale));
        } else {
            return format(LabelUtils.getLabel("listValues.notFound", locale), listValueId);
        }
    }


    public boolean updateOrCreate(ListValue listValue) {
        return _listValueDao.updateOrCreate(null, listValue);
    }


    /**
     * Inits system ListValues.<br>
     * IMPORTANT: Executed on each launch
     */
    public void initSystemListValues() {
        try {
            updateSystemListValueForType(ListValue.TYPE_DOC_RESOLUTION);
            updateSystemListValueForType(ListValue.TYPE_FORM_CATEGORY);
        } catch (Exception e) {
            LOG.error("Error during system ListValue initialization...", e);
        }
    }


    private void updateSystemListValueForType(int listValueSystemType) {
        boolean updated = false;
        ListValue listValue;

        switch (listValueSystemType) {
            case ListValue.TYPE_DOC_RESOLUTION:
                LOG.debug("Validating system ListValue for: SYSTEM_TYPE_DOC_RESOLUTION");
                listValue = _listValueDao.findByType(ListValue.TYPE_DOC_RESOLUTION);
                if (listValue == null) {
                    listValue = ListValue.createForDocs(DirectoryConstants.ELDOC_ROBOT);
                    updated = true;
                }
                listValue.setType(ListValue.TYPE_DOC_RESOLUTION);
                for (String langSysCode : I18n.LANG_SYSTEM_CODES_ALL) {
                    String systemLabel = LabelUtils.getLabel("listValue.default.docResolution", langSysCode);
                    if (isNotBlank(systemLabel) && !Objects.equals(listValue.getValue(langSysCode), systemLabel)) {
                        listValue.getValue().put(langSysCode, systemLabel);
                        updated = true;
                    }
                }
                if (updated) {
                    LOG.info("System ListValue was updated for: SYSTEM_TYPE_DOC_RESOLUTION");
                }
                break;

            case ListValue.TYPE_FORM_CATEGORY:
                LOG.debug("Validating system ListValue for: SYSTEM_TYPE_FORM_CATEGORY");
                listValue = _listValueDao.findByType(ListValue.TYPE_FORM_CATEGORY);
                if (listValue == null) {
                    listValue = ListValue.createForDocs(DirectoryConstants.ELDOC_ROBOT);
                }
                listValue.setType(ListValue.TYPE_FORM_CATEGORY);
                for (String langSysCode : I18n.LANG_SYSTEM_CODES_ALL) {
                    String systemLabel = LabelUtils.getLabel("listValue.default.formCategory", langSysCode);
                    if (isNotBlank(systemLabel) && !Objects.equals(listValue.getValue(langSysCode), systemLabel)) {
                        listValue.getValue().put(langSysCode, LabelUtils.getLabel("listValue.default.formCategory", langSysCode));
                        updated = true;
                    }
                }
                if (updated) {
                    LOG.info("System ListValue was updated for: SYSTEM_TYPE_FORM_CATEGORY");
                }
                break;

            default:
                LOG.warn("Unknown ListValue type '{}', skipping...", listValueSystemType);
                return;
        }

        if (updated) {
            if (!updateOrCreate(listValue)) {
                throw new ElDocRuntimeException("Error creating system ListValue model for type: " + listValueSystemType);
            }
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public ListValue getListValueDocResolution() {
        return _listValueDao.findByType(ListValue.TYPE_DOC_RESOLUTION);
    }


    public ListValue getListValueFormCategory() {
        return _listValueDao.findByType(ListValue.TYPE_FORM_CATEGORY);
    }


    public static ListValueService getInstance() {
        return CDI.current().select(ListValueService.class).get();
    }
}
