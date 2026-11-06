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

import ai.eldoc.bpm.ProcessStep;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.data.DirectorySimpleLazyDataModel;
import ai.eldoc.model.BasicModel;
import ai.eldoc.model.directory.*;
import ai.eldoc.service.DirectoryService;
import ai.eldoc.util.JsfUtils;
import ai.eldoc.util.LabelUtils;
import jakarta.el.ValueExpression;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.faces.annotation.RequestMap;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.event.SelectEvent;
import org.primefaces.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import static ai.eldoc.constants.DirectoryConstants.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Name Picker Bean
 */
@Named(value = NamePicker.BEAN_NAME)
@ViewScoped
public class NamePickerBean implements Serializable {

    @Serial
    private static final long serialVersionUID = -4231752328125047666L;
    private static final Logger LOG = LoggerFactory.getLogger(NamePickerBean.class);

    private static final int DIR_ALL = 0; // DirectoryService data for All - Persons and Groups
    private static final int DIR_GRPS = 2; // DirectoryService data for Groups
    private static final int DIR_BPMNAMES = 4; // DirectoryService data for BpmNames
    private static final int DIR_ROLES = 5; // DirectoryService data for BpmNames

    private static final String _INCLUDE_SINGLE = "/WEB-INF/includes/systemDialogs_namePicker_single.xhtml";
    private static final String _INCLUDE_MULTI = "/WEB-INF/includes/systemDialogs_namePicker_multi.xhtml";

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    @RequestMap
    private Map<String, Object> _requestMap;

    private ValueExpression _valueExpression;
    private Object _bindTo;
    private Object _objectBean;
    private boolean _groups;
    private boolean _bpmNames;
    private boolean _singleName;
    private String _refreshId;
    private boolean _disabled;
    private Set<String> _excludeIds;

    private List<DirectoryItem> _sourceDIListSelected = new ArrayList<>();
    private List<DirectoryItem> _targetDIList = new ArrayList<>();
    private List<DirectoryItem> _targetDIListSelected = new ArrayList<>();
    private DirectoryItem _sourceDISelected;
    private DirectoryItem _targetDISelected;
    private String _searchFT = null;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Resets values to initial state
     */
    private void resetBean() {
        _valueExpression = null;
        _sourceDIListSelected = new ArrayList<>();
        _targetDIList = new ArrayList<>();
        _targetDIListSelected = new ArrayList<>();
        _sourceDISelected = null;
        _targetDISelected = null;
        _searchFT = null;
    }


    public void initPicker(NamePicker namePikerInstance) {
        try {
            resetBean();

            Map<String, Object> attributes = namePikerInstance.getAttributes();
            LinkedHashSet<String> selectedIds = null;

            // Init attribute params
            _objectBean = attributes.get(NamePicker.ATTR_OBJECT_BEAN);
            _groups = getAttributeValue(attributes, NamePicker.ATTR_GROUPS, Boolean.FALSE);
            _bpmNames = getAttributeValue(attributes, NamePicker.ATTR_BPM_NAMES, Boolean.FALSE);
            _singleName = getAttributeValue(attributes, NamePicker.ATTR_SINGLE_NAME, Boolean.FALSE);
            _refreshId = getAttributeValue(attributes, NamePicker.ATTR_REFRESH_ID, (String) null);

            // Init already selected values
            _valueExpression = namePikerInstance.getValueExpression(NamePicker.ATTR_VALUE);
            if (_valueExpression != null) {
                selectedIds = getSelectedItems();
            }

            _bindTo = attributes.get(NamePicker.ATTR_BINDTO);
            if (_bindTo != null) {
                selectedIds = getCustomDataAsSet(_bindTo);
            }

            if (selectedIds == null) {
                selectedIds = new LinkedHashSet<>();
            }

            // Additionally excluded names
            _excludeIds = getCustomDataAsSet(getAttributeValue(attributes, NamePicker.ATTR_EXCLUDE_NAMES, new ArrayList<>()));

            selectedIds.forEach(selectedId -> {
                DirectoryItem di = _directoryService.getDirectoryItem(selectedId);
                if (di instanceof UndefinedNameItem) {
                    // UndefinedNameItem to be skipped as there are some unresolved issues with them on DataTable with selection options
                } else {
                    if (_singleName) {
                        _targetDIList.clear(); // In case we switched from multiple selection to single selection - make sure we got only one item in the selected list
                    }
                    _targetDIList.add(di);
                }
                // we also can exclude values from restrictions in case this values available in the target field:
                _excludeIds.removeIf(excludeId -> excludeId.equals(selectedId));
            });
        } catch (Exception e) {
            LOG.error("Error initializing NamePicker. {}", namePikerInstance.getAttributes(), e);
        }
    }


    private LinkedHashSet<String> getSelectedItems() {
        if (_valueExpression == null) {
            return new LinkedHashSet<>();
        }

        Object value = _valueExpression.getValue(JsfUtils.getFacesContext().getELContext());

        return switch (value) {
            case null -> new LinkedHashSet<>();

            case String selectedId -> {
                LinkedHashSet<String> result = new LinkedHashSet<>();
                if (!selectedId.isBlank()) {
                    result.add(selectedId);
                }
                yield result;
            }

            case Collection<?> selectedIds -> selectedIds.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(id -> !id.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            default -> {
                String id = String.valueOf(value);
                LinkedHashSet<String> result = new LinkedHashSet<>();
                if (!id.isBlank()) {
                    result.add(id);
                }
                yield result;
            }
        };
    }


    public void addSelected() {
        if (_singleName) {
            if (_sourceDISelected != null) {
                // to handle DataTable Single Select Mode
                _targetDIList.clear();
                _targetDIList.add(_sourceDISelected);
            }
        } else {
            _sourceDIListSelected.forEach(sourceDI -> {
                if (_targetDIList.stream().noneMatch(targetDI -> DirectoryService.isDirItemEqual(targetDI, sourceDI))) {
                    _targetDIList.add(sourceDI);
                }
            });
            _sourceDIListSelected.clear(); // Clear selection list for UI
        }
    }


    public void addSelectedOnDblClick(SelectEvent selectEvent) {
        if (selectEvent == null)
            return;

        if (selectEvent.getObject() instanceof DirectoryItem di) {
            if (!_targetDIList.stream().anyMatch(targetDI -> DirectoryService.isDirItemEqual(targetDI, di))) {
                _targetDIList.add(di);
            }
        } else {
            LOG.warn("Unexpected object type in addSelectedOnDblClick: {}", selectEvent.getObject().getClass().getName());
        }
    }


    public void removeSelected() {
        try {
            if (_singleName) {
                if (_targetDISelected != null) {
                    _targetDIList.removeIf(targetDI -> DirectoryService.isDirItemEqual(targetDI, _targetDISelected));
                    _targetDISelected = null;
                }
            } else {
                _sourceDIListSelected.removeIf(selectedDI -> {
                    return _targetDIListSelected.stream().anyMatch(selectedDIforRemove -> DirectoryService.isDirItemEqual(selectedDI, selectedDIforRemove));
                });
                _targetDIList.removeIf(targetDI -> {
                    return _targetDIListSelected.stream().anyMatch(selectedDIforRemove -> DirectoryService.isDirItemEqual(targetDI, selectedDIforRemove));
                });
            }
        } catch (Exception e) {
            LOG.error("Error removing selected items", e);
        }
    }


    public void removeSelectedOnDblClick(SelectEvent selectEvent) {
        try {
            if (selectEvent.getObject() instanceof DirectoryItem di) {
                _targetDIList.removeIf(targetDI -> DirectoryService.isDirItemEqual(targetDI, di));
                _sourceDIListSelected.removeIf(selectedSourceDI -> DirectoryService.isDirItemEqual(selectedSourceDI, di));
                _targetDIListSelected.removeIf(selectedTargetDI -> DirectoryService.isDirItemEqual(selectedTargetDI, di));
                _sourceDISelected = null;
                _targetDISelected = null;
            } else {
                LOG.warn("Unexpected object type in removeSelectedOnDblClick: {}", selectEvent.getObject().getClass().getName());
            }
        } catch (Exception e) {
            LOG.error("Error removing selected item", e);
        }
    }


    public void removeAll() {
        _targetDIList.clear();
        _sourceDIListSelected.clear();
        _targetDIListSelected.clear();
        _sourceDISelected = null;
        _targetDISelected = null;
    }


    public void btnOk() {
        try {
            addSelected();

            // In case object was passed - execute logic based on object class and, if needed, skip remaining actions
            if (_objectBean != null) {
                if (_objectBean instanceof ProcessStep) {
                    // In case if passed object is ProcessStep - execute logic for working with names on Process Step
                    ProcessStep procStep = (ProcessStep) _objectBean;
                    procStep.removeAllUsers();
                    procStep.removeAllGroups();
                    _targetDIList.forEach((dirItem) -> {
                        if (!dirItem.isGroupItem()) {
                            // Items which represent single systemId
                            procStep.addUser(dirItem.getIdSys());
                        } else {
                            // Items which represent group systemId
                            procStep.addGroup(dirItem.getIdSys());
                        }
                    });
                }
                return; // we can skip the rest of the actions here
            }

            if (_bindTo != null) {
                if (_bindTo instanceof Collection valueColl) {
                    valueColl.clear();
                    valueColl.addAll(getValue());
                } else if (_bindTo instanceof String) {
                    if (getValue().isEmpty()) {
                        _bindTo = null;
                    } else {
                        _bindTo = getValue().getFirst();
                    }
                }
            }

            if (_valueExpression != null) {
                setValueViaValueExpression();
            }
        } catch (Exception e) {
            LOG.error("Error setting value", e);
        }
    }


    /**
     * Sets value via ValueExpression
     */
    private void setValueViaValueExpression() {
        Class<?> targetType = _valueExpression.getType(JsfUtils.getFacesContext().getELContext());

        Object value;
        if (targetType != null && String.class.isAssignableFrom(targetType)) {
            value = getValue().isEmpty() ? null : getValue().getFirst();
        } else if (targetType != null && Set.class.isAssignableFrom(targetType)) {
            value = new LinkedHashSet<>(getValue());
        } else if (targetType != null && List.class.isAssignableFrom(targetType)) {
            value = getValue();
        } else {
            LOG.warn("Unexpected target data type: {}; using ArrayList as fallback", targetType);
            value = getValue();
        }

        _valueExpression.setValue(JsfUtils.getFacesContext().getELContext(), value);
    }


    private List<String> getValue() {
        List<String> targetStringList = new ArrayList<>();
        try {
            _targetDIList.forEach(item -> {
                if (!targetStringList.contains(item.getIdSys())) {
                    targetStringList.add(item.getIdSys()); // Add only unique values
                }
            });
        } catch (Exception e) {
            LOG.error("Error getting value", e);
        }
        return targetStringList;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public LazyDataModel<? extends BasicModel> getLazyDS() {
        try {
            DirectorySimpleLazyDataModel<? extends BasicModel> dirSimpleLazyModel = getBpmNamesLazyDataModel();
            dirSimpleLazyModel.getFilters().put(getDsField(), getSearchFT());
            dirSimpleLazyModel.getDefaultSortMeta().put(getSortBy().getField(), getSortBy());
            return dirSimpleLazyModel;
        } catch (Exception e) {
            LOG.error("Error getting directory lazy data model", e);
        }
        return null;
    }


    /**
     * Returns BpmNameItem List to be used in NamePicker
     */
    private static List<DirectoryItem> getBpmNameItems() {
        return Arrays.asList(
                new BpmNameItem(BPM_PERS_INITIATOR),
                new BpmNameItem(BPM_PERS_INITIATORCHIEF),
                new BpmNameItem(BPM_UNIT_INITIATOR)
        );
    }


    private DirectorySimpleLazyDataModel<? extends BasicModel> getBpmNamesLazyDataModel() {
        String CACHE_KEY = "__lazyInitBpmNames";

        if (!_requestMap.containsKey(CACHE_KEY)) {
            List<BasicModel> bpmNamesDS = new ArrayList();
            getBpmNameItems().forEach(di -> bpmNamesDS.add(di));
            _requestMap.put(CACHE_KEY, new DirectorySimpleLazyDataModel<>(bpmNamesDS));
        }

        return (DirectorySimpleLazyDataModel<DirectoryItem>) _requestMap.get(CACHE_KEY);
    }


    /**
     * Return specified attribute value or otherwise the specified default if it's null.
     */
    private <T> T getAttributeValue(Map<String, Object> attributes, String key, T defaultValue) {
        T value = (T) attributes.get(key);
        return (value != null) ? value : defaultValue;
    }


    private String getDsField() {
        return "itemNameUI";
    }


    private SortMeta getSortBy() {
        return SortMeta.builder().field(getDsField()).order(SortOrder.ASCENDING).build();
    }


    /**
     * Returns Set of values based on custom input data (String/List/Set)
     *
     * @param value containing custom data (String/List/Set) to be converted to Set
     * @return
     */
    public LinkedHashSet<String> getCustomDataAsSet(Object value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value == null) {
            return result;
        }

        switch (value) {
            case List list -> result.addAll(list);
            case Set set -> result.addAll(set);
            case String stringValue -> {
                if (isNotBlank(stringValue)) {
                    result.add(stringValue);
                }
            }
            default -> {
                String stringValue = String.valueOf(value);
                if (isNotBlank(stringValue)) {
                    result.add(stringValue);
                }
            }
        }

        return result;
    }


    public boolean isDisabledSelection(DirectoryItem rowData) {
        return _excludeIds.contains(rowData.getIdSys()) || _targetDIList.stream().anyMatch(di -> DirectoryService.isDirItemEqual(di, rowData));
    }


    public List<DirectoryItem> getSourceDIListSelected() {
        return _sourceDIListSelected;
    }


    public void setSourceDIListSelected(List<DirectoryItem> sourceDIListSelected) {
        _sourceDIListSelected = sourceDIListSelected;
    }


    public List<DirectoryItem> getTargetDIList() {
        return _targetDIList;
    }


    public void setTargetDIList(List<DirectoryItem> targetDIList) {
        _targetDIList = targetDIList;
    }


    public List<DirectoryItem> getTargetDIListSelected() {
        return _targetDIListSelected;
    }


    public void setTargetDIListSelected(List<DirectoryItem> targetDIListSelected) {
        _targetDIListSelected = targetDIListSelected;
    }


    public DirectoryItem getSourceDISelected() {
        return _sourceDISelected;
    }


    public void setSourceDISelected(DirectoryItem sourceDISelected) {
        _sourceDISelected = sourceDISelected;
    }


    public DirectoryItem getTargetDISelected() {
        return _targetDISelected;
    }


    public void setTargetDISelected(DirectoryItem targetDISelected) {
        _targetDISelected = targetDISelected;
    }


    /**
     * @return List of SelectItems for selecting directory
     */
    public List<SelectItem> getSelectedDirectorySI() {
        List<SelectItem> selectDirs = new ArrayList<>();
        String siPrefix = LabelUtils.getLabel("ccNamePicker.selectedDirectory", _localeContext.getLocale());
        if (_groups) {
            selectDirs.add(new SelectItem(DIR_GRPS, siPrefix.concat(": ").concat(_localeContext.getLabel("ccNamePicker.groups"))));
        }
        if (_bpmNames) {
            selectDirs.add(new SelectItem(DIR_BPMNAMES, _localeContext.getLabel("ccNamePicker.bpmNames")));
        }
        return selectDirs;
    }


    public boolean isSelectedDirectoryRendered() {
        return (getSelectedDirectorySI().size() > 1);
    }


    public boolean isSingleName() {
        return _singleName;
    }


    public String getRefreshId() {
        return _refreshId;
    }


    public String getSearchFT() {
        return _searchFT;
    }


    public void setSearchFT(String searchFT) {
        _searchFT = searchFT;
    }


    public String getNamePikerContentSrc() {
        return (_singleName ? _INCLUDE_SINGLE : _INCLUDE_MULTI);
    }


    public static NamePickerBean getInstance() {
        return CDI.current().select(NamePickerBean.class).get();
    }

}
