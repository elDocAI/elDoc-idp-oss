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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.context.UserContext;
import ai.eldoc.model.ListValue;
import ai.eldoc.service.ListValueService;
import ai.eldoc.service.LogHelper;
import ai.eldoc.util.JsfUtils;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.event.RowEditEvent;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * List Values (Directories) Controller
 */
@Named(value = "lvController")
@ViewScoped
public class ListValueController implements Serializable {

    @Serial
    private static final long serialVersionUID = 2016844929597543728L;
    private static final Logger LOG = Logger.getLogger(ListValueController.class.getName());

    @Inject
    ListValueService _listValueService;

    @Inject
    private UserContext _userContext;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private PageController _pageController;

    private List<ListValue> _allLVItemsList;
    private ListValue _activeLVItem;
    private ListValue _selectedListValueItem;
    private String _id;
    private boolean _showDeletedItems = false;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    public void createItem() {
        try {
            _selectedListValueItem = ListValue.createForDocs(_userContext.getId());
            if (_activeLVItem != null) {
                // set relation
                _selectedListValueItem.setIdKf(_activeLVItem.getId());
                // inherit access rights from the parent ListValue
                _selectedListValueItem.getAcl().set(_activeLVItem.getAcl());
            }
        } catch (Exception e) {
            LogHelper.setEvent("id00318", e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
            JsfUtils.keepMessagesOnRedirect();
            _pageController.reloadPage();
        }
    }


    public void selectItem(ListValue lvItem) {
        _activeLVItem = lvItem;
    }


    public void onRowEdit(RowEditEvent rowEditEvent) {
        try {
            ListValue lvItem = (ListValue) rowEditEvent.getObject();
            if (!_listValueService.updateOrCreate(lvItem)) {
                throw new ElDocRuntimeException("ListValue item save error");
            }
            _allLVItemsList = null; // we need to reset the list after any change to the ListValue models
        } catch (Exception e) {
            LogHelper.setEvent("id00319", e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
            JsfUtils.keepMessagesOnRedirect();
            _pageController.reloadPage();
        }
    }


    public void saveItem() {
        try {
            if (!_listValueService.updateOrCreate(_selectedListValueItem)) {
                throw new ElDocRuntimeException("ListValue item save error");
            }
            _selectedListValueItem = null;
            _allLVItemsList = null; // we need to reset the list after any change to the ListValue models
        } catch (Exception e) {
            LogHelper.setEvent("id00320", e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
            JsfUtils.keepMessagesOnRedirect();
            _pageController.reloadPage();
        }
    }


    public void cancelItemEdit() {
        _selectedListValueItem = null;
    }


    public void restoreItem(ListValue lvItem) {
        try {
            lvItem.setStatus(ListValue.STATUS_NORMAL);
            _listValueService.updateOrCreate(lvItem);
            _allLVItemsList = null; // we need to reset the list after any change to the ListValue models
        } catch (Exception e) {
            LOG.log(Level.SEVERE, lvItem.getId(), e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
            JsfUtils.keepMessagesOnRedirect();
            _pageController.reloadPage();
        }
    }


    public void resetAccesses(ListValue lvItem) {
        try {
            lvItem.getAcl().getR().clear();
            lvItem.getAcl().getR().add(DirectoryConstants.ROLE_ALLUSERS);
            lvItem.getAcl().getRH().clear();
            _listValueService.updateOrCreate(lvItem);
            JsfUtils.addInfoMessage(_localeContext.getLabel("listValueAdminUI.msg.resetAclSuccess"));
            _allLVItemsList = null; // we need to reset the list after any change to the ListValue models
        } catch (Exception e) {
            LOG.log(Level.SEVERE, lvItem.getId(), e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
            JsfUtils.keepMessagesOnRedirect();
            _pageController.reloadPage();
        }
    }


    public void toggleShowDeletedItems() {
        _showDeletedItems = !_showDeletedItems;
        _allLVItemsList = null;
    }


    /**
     * Checks whether given ListValue model has records
     *
     * @param lv
     * @return
     */
    public boolean isEmpty(ListValue lv) {
        return getAllListValuesByKF(lv.getId()).isEmpty();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public List<ListValue> getAllListValues() {
        if (_allLVItemsList == null) {
            // In order to support dataTable rowEdit functionality - we need to cache list of models
            if (_activeLVItem != null) {
                _allLVItemsList = _listValueService.getAllByKF(_activeLVItem.getId(), null, null, _showDeletedItems, _userContext.getAclContext().toFullAccessMode()); // FullAccess AclContext for admin page
            } else {
                _allLVItemsList = _listValueService.getAllByKF(null, null, null, _showDeletedItems, _userContext.getAclContext().toFullAccessMode()); // FullAccess AclContext for admin page
            }
            _allLVItemsList.sort(Comparator.comparing(listValue -> listValue.getValue(_localeContext.getLangSystemCode())));
        }
        return _allLVItemsList;
    }


    public List<SelectItem> getAllListValuesSI() {
        List<SelectItem> result = new ArrayList<>();
        _listValueService.getAllByKF(null, _userContext.getAclContext().toFullAccessMode()).stream()
                .sorted(Comparator.comparing(listValue -> listValue.getValue(_localeContext.getLangSystemCode())))
                .forEach(listValue -> {
                    result.add(new SelectItem(listValue.getId(), listValue.getValue(_localeContext.getLangSystemCode())));
                });
        return result;
    }


    /**
     * Finds and returns ListValue data for the current locale
     *
     * @param listValueId
     * @return
     */
    public String getLVData(String listValueId) {
        return _listValueService.getLVData(listValueId, _localeContext.getLocale());
    }


    public ListValue getSelectedListValueItem() {
        return _selectedListValueItem;
    }


    public ListValue getActiveListValueItem() {
        return _activeLVItem;
    }


    public String getActiveListValueLabel() {
        return _activeLVItem.getValue(_localeContext.getLangSystemCode());
    }


    public List<ListValue> getAllListValuesByKF(String keyForeign) {
        List<ListValue> result = _listValueService.getAllByKF(keyForeign, _userContext.getAclContext());
        result.sort(Comparator.comparing(listValue -> listValue.getValue(_localeContext.getLangSystemCode())));
        return result;
    }


    public List<SelectItem> getResolutionListValuesSI() {
        List<SelectItem> result = new ArrayList<>();
        try {
            ListValue lvResol = _listValueService.getListValueDocResolution();
            List<ListValue> sysValueList = getAllListValuesByKF(lvResol.getId());
            sysValueList.forEach(sysValue -> result.add(new SelectItem(sysValue.getId(), sysValue.getValue(_localeContext.getLangSystemCode()))));
        } catch (Exception e) {
            LogHelper.setEvent("id00316", e);
        }
        return result;
    }


    public List<SelectItem> getWorkflowCategorySI() {
        List<SelectItem> result = new ArrayList<>();
        result.add(new SelectItem(null, " ")); // add the default value first
        try {
            ListValue lvFormCat = _listValueService.getListValueFormCategory();
            List<ListValue> sysValueList = getAllListValuesByKF(lvFormCat.getId());
            sysValueList.forEach(sysValue -> result.add(new SelectItem(sysValue.getId(), sysValue.getValue(_localeContext.getLangSystemCode()))));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, null, e);
        }
        return result;
    }


    public String getId() {
        return _id;
    }


    public void setId(String id) {
        _id = id;
    }


    /**
     * Defines whether ListValue control buttons should be rendered on the view
     *
     * @return
     */
    public boolean isListValueControlsAreaRendered() {
        if (_activeLVItem == null) {
            // when nothing selected - only AdminDocRW can create new records
            return _userContext.isAdminDocRW();
        } else {
            return true;
        }
    }


    /**
     * Defines whether btnAdd rendered
     *
     * @return
     */
    public boolean isBtnAddRendered() {
        return _userContext.isAdminDocRW();
    }


    public boolean isShowDeletedItems() {
        return _showDeletedItems;
    }

}
