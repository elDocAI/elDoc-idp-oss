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

import ai.eldoc.dao.WorkflowDao;
import ai.eldoc.data.WorkflowLazyDataModel;
import ai.eldoc.data.WorkflowLazyQuery;
import ai.eldoc.model.Workflow;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.ui.view.FormDefController;
import ai.eldoc.ui.view.PageController;
import ai.eldoc.util.JsfUtils;
import jakarta.el.ValueExpression;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.faces.component.UIComponent;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.ViewScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Referenced Workflows Picker
 */
@Named(value = RefWFPicker.BEAN_NAME)
@ViewScoped
public class RefWFPickerBean implements Serializable {

    @Serial
    private static final long serialVersionUID = 7733800350535203998L;
    private static final Logger LOG = LoggerFactory.getLogger(RefWFPickerBean.class);

    private static final String _INCLUDE_SINGLE = "/WEB-INF/includes/systemDialogs_docPicker_single.xhtml";
    private static final String _INCLUDE_MULTI = "/WEB-INF/includes/systemDialogs_docPicker_multi.xhtml";

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private FormDefController _formDefController;

    @Inject
    private PageController _pageController;

    private String _refreshId;
    private boolean _singleSelect;
    private ValueExpression _valueExpression;

    private WorkflowLazyDataModel _dataProvider;
    private final List<Workflow> _selectedList = new ArrayList<>();
    private Workflow _selected;

    private transient UIComponent _refWFPickerInstance;
    private String _refWFPickerClientId;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Resets values to the initial state
     */
    private void resetBean() {
        _refreshId = null;
        _singleSelect = false;
        _valueExpression = null;
        _dataProvider = null;
        _selectedList.clear();
        _selected = null;
        _refWFPickerInstance = null;
        _refWFPickerClientId = null;
    }


    public void initPicker(RefWFPicker refWFPickerInstance) {
        try {
            resetBean();

            Map<String, Object> attributes = refWFPickerInstance.getAttributes();

            // Init attribute params
            _valueExpression = refWFPickerInstance.getValueExpression(RefWFPicker.ATTR_VALUE);
            _refreshId = getAttributeValue(attributes, RefWFPicker.ATTR_REFRESH_ID, (String) null);
            _singleSelect = getAttributeValue(attributes, RefWFPicker.ATTR_SINGLE_SELECT, false);

            _refWFPickerInstance = refWFPickerInstance;
            _refWFPickerClientId = refWFPickerInstance.getClientId(JsfUtils.getFacesContext());

            List<String> selectedRefIds = getSelectedRefIds();
            if (selectedRefIds != null && !selectedRefIds.isEmpty()) {
                if (_singleSelect) {
                    _selected = _workflowDao.find(selectedRefIds.getFirst(), SecurityUtils.getSubjectAclContext());
                } else {
                    selectedRefIds.stream()
                            .map(selectedWorkflowId -> _workflowDao.find(selectedWorkflowId, SecurityUtils.getSubjectAclContext()))
                            .filter(Objects::nonNull)
                            .forEachOrdered(_selectedList::add);
                }
            }

            _dataProvider = new WorkflowLazyDataModel(_workflowDao, WorkflowLazyQuery.VIEW.FORREF, _pageController.isModuleIdp(), SecurityUtils.getSubjectAclContext());

            List<Integer> stateFilter = _formDefController.getWorkflowStateSI(false).stream()
                    .map(SelectItem::getValue)
                    .map(i -> (Integer) i)
                    .collect(Collectors.toList());
            _dataProvider.setStateFilter(stateFilter);
            List<Integer> statusFilter = _formDefController.getWorkflowStatusSI().stream()
                    .map(SelectItem::getValue)
                    .map(i -> (Integer) i)
                    .collect(Collectors.toList());
            _dataProvider.setStatusFilter(statusFilter);
            List<String> formFilter = _formDefController.getSearchDocTitleLabelsSI().stream()
                    .map(SelectItem::getValue)
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            _dataProvider.setFormFilter(formFilter);
        } catch (Exception e) {
            LOG.error("Init Picker failed", e);
        }
    }


    public void btnOk() {
        if (_valueExpression != null) {
            UIComponent refWFPickerInstance = getRefWFPickerInstance();
            if (refWFPickerInstance == null) {
                LOG.warn("RefWFPicker component instance was not found in current view - could not register it in context. Evaluating #{cc.X} expressions may fail.");
            }
            try {
                // #pushComponentToEL allows properly evaluating #{cc.X} expressions in custom components, as otherwise composite component is missing from elContext and `cc` resolves to null.
                if (refWFPickerInstance != null) {
                    refWFPickerInstance.pushComponentToEL(JsfUtils.getFacesContext(), refWFPickerInstance);
                }
                Class<?> targetType = _valueExpression.getType(JsfUtils.getFacesContext().getELContext());
                _valueExpression.setValue(JsfUtils.getFacesContext().getELContext(), resolveValue(getValue(), targetType));
            } finally {
                if (refWFPickerInstance != null) {
                    refWFPickerInstance.popComponentFromEL(JsfUtils.getFacesContext());
                }
            }
        }
    }


    private Object resolveValue(List<String> values, Class<?> targetType) {
        if (targetType == null) {
            LOG.warn("Unable to resolve target data type; clearing value with null");
            return null;
        }

        if (String.class.isAssignableFrom(targetType)) {
            return values.isEmpty() ? null : values.getFirst();
        } else if (Set.class.isAssignableFrom(targetType)) {
            return new LinkedHashSet<>(values);
        } else if (List.class.isAssignableFrom(targetType)) {
            return new ArrayList<>(values);
        } else {
            // For CustomField, targetType is Object, as picker targets element in Map<String, Object> _cfData (this is the only <cc:refWfPicker ...> element with _singleSelect = true)
            if (_singleSelect) {
                return values.isEmpty() ? null : values.getFirst();
            } else {
                LOG.warn("Unexpected target data type: {}; using ArrayList as fallback", targetType);
                return new ArrayList<>(values);
            }
        }
    }


    private List<String> getValue() {
        if (_singleSelect) {
            return (_selected != null) ? List.of(_selected.getId()) : Collections.emptyList();
        } else {
            return _selectedList.stream()
                    .map(Workflow::getId)
                    .collect(Collectors.toList());
        }
    }


    private UIComponent getRefWFPickerInstance() {
        if (_refWFPickerInstance != null) {
            return _refWFPickerInstance;
        }
        if (_refWFPickerClientId == null) {
            return null;
        }

        _refWFPickerInstance = findComponentByClientId(JsfUtils.getFacesContext().getViewRoot(), _refWFPickerClientId);
        return _refWFPickerInstance;
    }


    private UIComponent findComponentByClientId(UIComponent component, String clientId) {
        if (component == null) {
            return null;
        }
        if (clientId.equals(component.getClientId(JsfUtils.getFacesContext()))) {
            return component;
        }
        for (UIComponent child : component.getChildren()) {
            UIComponent found = findComponentByClientId(child, clientId);
            if (found != null) {
                return found;
            }
        }
        for (UIComponent facet : component.getFacets().values()) {
            UIComponent found = findComponentByClientId(facet, clientId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    private List<String> getSelectedRefIds() {
        if (_valueExpression == null) {
            return Collections.emptyList();
        }

        Object value = _valueExpression.getValue(JsfUtils.getFacesContext().getELContext());

        if (value == null) {
            return Collections.emptyList();
        }

        if (value instanceof String selectedRefId) {
            return selectedRefId.isBlank()
                    ? Collections.emptyList()
                    : List.of(selectedRefId);
        }

        if (value instanceof Collection<?> selectedDocIds) {
            return selectedDocIds.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(id -> !id.isBlank())
                    .collect(Collectors.toList());
        }

        return List.of(String.valueOf(value));
    }


    /**
     * Return specified attribute value or otherwise the specified default if it's null.
     */
    private <T> T getAttributeValue(Map<String, Object> attributes, String key, T defaultValue) {
        T value = (T) attributes.get(key);
        return (value != null) ? value : defaultValue;
    }


    public WorkflowLazyDataModel getDataProvider() {
        return _dataProvider;
    }


    public List<Workflow> getSelectedList() {
        return _selectedList;
    }


    public void setSelectedList(List<Workflow> selectedList) {
        _selectedList.clear();
        if (selectedList != null && !selectedList.isEmpty()) {
            _selectedList.addAll(selectedList);
        }
    }


    public Workflow getSelected() {
        return _selected;
    }


    public void setSelected(Workflow selected) {
        _selected = selected;
    }


    public String getRefreshId() {
        return _refreshId;
    }


    public String getIncludeSrc() {
        return (_singleSelect ? _INCLUDE_SINGLE : _INCLUDE_MULTI);
    }


    public static RefWFPickerBean getInstance() {
        return CDI.current().select(RefWFPickerBean.class).get();
    }
}
