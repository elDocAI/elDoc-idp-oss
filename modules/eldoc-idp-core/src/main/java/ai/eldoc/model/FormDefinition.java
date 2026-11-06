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

package ai.eldoc.model;

import ai.eldoc.annotation.I18nSupport;
import ai.eldoc.constants.DirectoryConstants;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Period;
import java.util.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Form Definition Model
 */
@I18nSupport(fields = {FormDefinition._NAME})
public class FormDefinition implements BasicMetaAwareModel, BasicAuthorAwareModel, CustomFieldAwareModel, FileTemplateAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = 2429097037138535400L;

    public static final String _NAME = "nm"; // Map containing human-friendly name of the form per language, e.g.: Invoice, Contract, etc.
    public static final String _AUTHOR = "author";
    public static final String _STATUS = "stts";
    public static final String _TYPE = "typ"; // Form type: 0 - static, 1 - dynamic
    public static final String _BPM_PROCESS_DEF_ID = "prcsId";
    public static final String _CATEGORY_ID = "catId"; // ID of the ListValue model to which this formDef belongs
    public static final String _LAYOUT_TYPE = "lytTyp"; // Form Layout Type: 0 - default, 1 - N - other types
    public static final String _SF_DEF = "_sfDef"; // Standard Fields definition
    public static final String _ACCESS_CREATE = "accsCrt"; // List of users who have right to create workflows with this form
    public static final String _ACCESS_READ = "accRd"; // List of users added by default with Read access to the created workflows with this form
    public static final String _ACCESS_READ_EDITABLE = "accRdEdt"; // Defines if list of users with read access is editable
    public static final String _ACCESS_EDIT_BY_ADMIN_ON_STATE = "accEdtAdmStt"; // Allow Workflow edit by AdminDocRW role on specified state
    public static final String _ACCESS_EDIT_BY_AUTHOR_ON_STATE = "accEdtAthrStt"; // Allow Workflow edit by Author on specified state
    public static final String _RETENTION_ACTION = "ret"; // Retention action to be performed
    public static final String _RETENTION_PERIOD = "retPd"; // Retention period to be used
    public static final String _POST_CREATE_HOOK = "hkPstCrt"; // Post-create hook
    public static final String _TEMPLATES = FileTemplateAwareModel._TEMPLATES; // Stores Templates data

    /**
     * Draft (just created)
     */
    public static final int STATUS_DRAFT = 0;

    /**
     * Active
     */
    public static final int STATUS_ACTIVE = 1;

    /**
     * Suspended
     */
    public static final int STATUS_SUSPENDED = 2;

    /**
     * Static form (embedded into the system or created manually)
     */
    public static final int TYPE_STATIC = 0;

    /**
     * Dynamic form - crated by end-user
     */
    public static final int TYPE_DYNAMIC = 1;

    /**
     * Retention action: Default (keep document indefinitely). No retention action to be performed.
     */
    public static final int RETENTION_ACTION_KEEP = 0;

    /**
     * Retention action: Block document after a retention period reached.
     */
    public static final int RETENTION_ACTION_BLOCK = 1; // old was TASK_STATUS_BLOCK=110

    /**
     * Retention action: Delete document after a retention period reached.
     */
    public static final int RETENTION_ACTION_DELETE = 2; // old was TASK_STATUS_DELETE=111


    /**
     * Default layout type
     */
    public static final int LAYOUT_TYPE_DEFAULT = 0;


    public static final String FORM_ID_STATIC_PREFIX = "f";
    public static final String FORM_ID_DYNAMIC_PREFIX = "fd";
    public static final String FORM_ID_INTEGRATION_PREFIX = "fi";
    public static final String FORM_ID_RECO = "frd1";


    private String _id;
    private String _id_web; // Not used in Global collections
    private final Map<String, String> _name;
    private String _author;
    private int _status;
    private int _type;
    private final Set<String> _processIds;
    private String _categoryId;
    private int _layoutType;
    private StandardFieldDef _sfDef;
    private final List<CustomField> _cfDef;
    private final Set<String> _accessCreate;
    private final Set<String> _accessRead;
    private boolean _accessReadEditable;
    private final Set<Integer> _accessEditByAdminOnState;
    private final Set<Integer> _accessEditByAuthorOnState;
    private int _retentionAction;
    private String _retentionPeriod;
    private String _postCreateHook;
    private final List<TemplateRef> _templates;
    private Meta _meta;

    //
    // Cunstructors -------------------------------------------------------------------------------
    //


    /**
     * Constructor for Mongo Java Driver POJO Support
     */
    protected FormDefinition() {
        this(null);
    }


    /**
     * Creates a new model with the given arguments and initializes basic fields
     *
     * @param authorSystemId author systemId
     */
    public FormDefinition(String authorSystemId) {
        _name = new HashMap<>();
        _author = authorSystemId;
        _sfDef = new StandardFieldDef();
        _cfDef = new ArrayList<>();
        _processIds = new LinkedHashSet<>();
        _accessCreate = new LinkedHashSet<>();
        _accessRead = new LinkedHashSet<>();
        _accessEditByAuthorOnState = new LinkedHashSet<>();
        _accessEditByAdminOnState = new LinkedHashSet<>();
        _templates = new ArrayList<>();
        _meta = new Meta();

        // Init fields
        setType(TYPE_DYNAMIC);
        setAccessCreate(Set.of(DirectoryConstants.ROLE_ALLUSERS));
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Returns the workflow name for the given lang key
     *
     * @param langCode lang system code
     * @return workflow name for the given lang key, or "Not set for lang: [" + langCode + "]" if not set
     */
    public String getNameByLang(String langCode) {
        return _name.getOrDefault(langCode, "Not set for lang: [" + langCode + "]");
    }


    /**
     * Sets a workflow name for the given lang key
     *
     * @param langCode lang system code
     * @param value in case value is not given {@code null} or empty - removes give langCode, otherwise sets the give value
     */
    public void setNameByLang(String langCode, String value) {
        if (value == null || value.isBlank()) {
            _name.remove(langCode);
        } else {
            _name.put(langCode, value);
        }
    }


    //
    // Getters & Setters business logic (virtual) -------------------------------------------------
    //


    /**
     * Returns FormId (uses _id_web). FormId looks like: fd1, fd15, etc.
     *
     * @return FormId (uses _id_web). FormId looks like: fd1, fd15, etc.
     */
    @BsonIgnore
    @JsonbTransient
    public String getFormId() {
        return getIdWeb();
    }


    /**
     * Sets FormId (uses _id_web). FormId looks like: fd1, fd15, etc.
     *
     * @param formId FormId (uses _id_web). FormId looks like: fd1, fd15, etc.
     */
    @BsonIgnore
    @JsonbTransient
    public void setFormId(String formId) {
        setIdWeb(formId);
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isActive() {
        return STATUS_ACTIVE == getStatus();
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isSuspended() {
        return STATUS_SUSPENDED == getStatus();
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeDynamic() {
        return getType() == TYPE_DYNAMIC;
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeStatic() {
        return getType() == TYPE_STATIC;
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isAccessCreateAvailableToAll() {
        return getAccessCreate().contains(DirectoryConstants.ROLE_ALLUSERS);
    }


    @BsonIgnore
    @JsonbTransient
    public void setAccessCreateAvailableToAll(boolean availableToAll) {
        if (availableToAll) {
            getAccessCreate().add(DirectoryConstants.ROLE_ALLUSERS);
        } else {
            getAccessCreate().remove(DirectoryConstants.ROLE_ALLUSERS);
        }
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isAccessReadAvailableToAll() {
        return getAccessRead().contains(DirectoryConstants.ROLE_ALLUSERS);
    }


    @BsonIgnore
    @JsonbTransient
    public void setAccessReadAvailableToAll(boolean availableToAll) {
        if (availableToAll) {
            getAccessRead().add(DirectoryConstants.ROLE_ALLUSERS);
        } else {
            getAccessRead().remove(DirectoryConstants.ROLE_ALLUSERS);
        }
    }


    /**
     * Returns true when this Form is a Recognition Workflow form
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public boolean isRecoWFForm() {
        return isRecoWFForm(getFormId());
    }


    @BsonIgnore
    @JsonbTransient
    public Period getRetentionPeriod() {
        if (isNotBlank(getRetentionPeriodStr())) {
            return Period.parse(getRetentionPeriodStr());
        }
        return null;
    }


    @BsonIgnore
    @JsonbTransient
    public void setRetentionPeriod(Period period) {
        if (period == null) {
            setRetentionPeriodStr(null);
        } else {
            setRetentionPeriodStr(period.toString());
        }
    }


    @BsonIgnore
    @JsonbTransient
    public String getPostCreateHookDecoded() throws ParseException {
        if (isBlank(getPostCreateHook())) {
            return null;
        }
        return new String(Base64.getDecoder().decode(getPostCreateHook()), StandardCharsets.UTF_8);
    }


    //
    // Static business methods, getters & setters -------------------------------------------------
    //


    /**
     * Returns true when given form ID is a Recognition Workflow form
     *
     * @param formId form ID (e.g.: frd1, fd5, etc.)
     * @return true when given form ID is a Recognition Workflow form
     */
    public static boolean isRecoWFForm(String formId) {
        return FORM_ID_RECO.equals(formId);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    @BsonProperty(_ID)
    @JsonbProperty(_ID)
    public String getId() {
        return _id;
    }


    @BsonProperty(_ID)
    @JsonbProperty(_ID)
    public void setId(String id) {
        _id = id;
    }


    @BsonProperty(_ID_WEB)
    @JsonbProperty(_ID_WEB)
    public String getIdWeb() {
        return _id_web;
    }


    @BsonProperty(_ID_WEB)
    @JsonbProperty(_ID_WEB)
    public void setIdWeb(String idWeb) {
        _id_web = idWeb;
    }


    @BsonProperty(_NAME)
    @JsonbProperty(_NAME)
    public Map<String, String> getName() {
        return _name;
    }


    @BsonProperty(_NAME)
    @JsonbProperty(_NAME)
    public void setName(Map<String, String> name) {
        _name.clear();
        _name.putAll(MapUtils.emptyIfNull(name));
    }


    @Override
    @BsonProperty(_AUTHOR)
    @JsonbProperty(_AUTHOR)
    public String getAuthor() {
        return _author;
    }


    @BsonProperty(_AUTHOR)
    @JsonbProperty(_AUTHOR)
    public void setAuthor(String author) {
        _author = author;
    }


    @BsonProperty(_STATUS)
    @JsonbProperty(_STATUS)
    public int getStatus() {
        return _status;
    }


    @BsonProperty(_STATUS)
    @JsonbProperty(_STATUS)
    public void setStatus(int status) {
        _status = status;
    }


    @BsonProperty(_TYPE)
    @JsonbProperty(_TYPE)
    public int getType() {
        return _type;
    }


    @BsonProperty(_TYPE)
    @JsonbProperty(_TYPE)
    public void setType(int type) {
        _type = type;
    }


    @BsonProperty(_BPM_PROCESS_DEF_ID)
    @JsonbProperty(_BPM_PROCESS_DEF_ID)
    public Set<String> getProcessIds() {
        return _processIds;
    }


    @BsonProperty(_BPM_PROCESS_DEF_ID)
    @JsonbProperty(_BPM_PROCESS_DEF_ID)
    public void setProcessIds(Set<String> processIds) {
        _processIds.clear();
        _processIds.addAll(CollectionUtils.emptyIfNull(processIds));
    }


    @BsonProperty(_CATEGORY_ID)
    @JsonbProperty(_CATEGORY_ID)
    public String getCategoryId() {
        return _categoryId;
    }


    @BsonProperty(_CATEGORY_ID)
    @JsonbProperty(_CATEGORY_ID)
    public void setCategoryId(String categoryId) {
        _categoryId = categoryId;
    }


    @BsonProperty(_LAYOUT_TYPE)
    @JsonbProperty(_LAYOUT_TYPE)
    public int getLayoutType() {
        return _layoutType;
    }


    @BsonProperty(_LAYOUT_TYPE)
    @JsonbProperty(_LAYOUT_TYPE)
    public void setLayoutType(int layoutType) {
        _layoutType = layoutType;
    }


    @BsonProperty(_SF_DEF)
    @JsonbProperty(_SF_DEF)
    public StandardFieldDef getSfDef() {
        return _sfDef;
    }


    @BsonProperty(_SF_DEF)
    @JsonbProperty(_SF_DEF)
    public void setSfDef(StandardFieldDef sfDef) {
        _sfDef = sfDef;
    }


    @Override
    @BsonProperty(_CF_DEF)
    @JsonbProperty(_CF_DEF)
    public List<CustomField> getCfDef() {
        return _cfDef;
    }


    @BsonProperty(_CF_DEF)
    @JsonbProperty(_CF_DEF)
    public void setCfDef(List<CustomField> cfDef) {
        _cfDef.clear();
        _cfDef.addAll(CollectionUtils.emptyIfNull(cfDef));
    }


    @Override
    @BsonIgnore
    @JsonbTransient
    /**
     * CustomField Data is not used with this model
     */
    public Map<String, Object> getCfData() {
        return null;
    }


    @BsonProperty(_ACCESS_CREATE)
    @JsonbProperty(_ACCESS_CREATE)
    public Set<String> getAccessCreate() {
        return _accessCreate;
    }


    @BsonProperty(_ACCESS_CREATE)
    @JsonbProperty(_ACCESS_CREATE)
    public void setAccessCreate(Set<String> accessCreate) {
        _accessCreate.clear();
        _accessCreate.addAll(CollectionUtils.emptyIfNull(accessCreate));
    }


    @BsonProperty(_ACCESS_READ)
    @JsonbProperty(_ACCESS_READ)
    public Set<String> getAccessRead() {
        return _accessRead;
    }


    @BsonProperty(_ACCESS_READ)
    @JsonbProperty(_ACCESS_READ)
    public void setAccessRead(Set<String> accessRead) {
        _accessRead.clear();
        _accessRead.addAll(CollectionUtils.emptyIfNull(accessRead));
    }


    @BsonProperty(_ACCESS_READ_EDITABLE)
    @JsonbProperty(_ACCESS_READ_EDITABLE)
    public boolean isAccessReadEditable() {
        return _accessReadEditable;
    }


    @BsonProperty(_ACCESS_READ_EDITABLE)
    @JsonbProperty(_ACCESS_READ_EDITABLE)
    public void setAccessReadEditable(boolean accessReadEditable) {
        _accessReadEditable = accessReadEditable;
    }


    @BsonProperty(_ACCESS_EDIT_BY_ADMIN_ON_STATE)
    @JsonbProperty(_ACCESS_EDIT_BY_ADMIN_ON_STATE)
    public Set<Integer> getAccessEditByAdminOnState() {
        return _accessEditByAdminOnState;
    }


    @BsonProperty(_ACCESS_EDIT_BY_ADMIN_ON_STATE)
    @JsonbProperty(_ACCESS_EDIT_BY_ADMIN_ON_STATE)
    public void setAccessEditByAdminOnState(Set<Integer> accessEditByAdminOnState) {
        _accessEditByAdminOnState.clear();
        _accessEditByAdminOnState.addAll(CollectionUtils.emptyIfNull(accessEditByAdminOnState));
    }


    @BsonProperty(_ACCESS_EDIT_BY_AUTHOR_ON_STATE)
    @JsonbProperty(_ACCESS_EDIT_BY_AUTHOR_ON_STATE)
    public Set<Integer> getAccessEditByAuthorOnState() {
        return _accessEditByAuthorOnState;
    }


    @BsonProperty(_ACCESS_EDIT_BY_AUTHOR_ON_STATE)
    @JsonbProperty(_ACCESS_EDIT_BY_AUTHOR_ON_STATE)
    public void setAccessEditByAuthorOnState(Set<Integer> accessEditByAuthorOnState) {
        _accessEditByAuthorOnState.clear();
        _accessEditByAuthorOnState.addAll(CollectionUtils.emptyIfNull(accessEditByAuthorOnState));
    }


    @BsonProperty(_RETENTION_ACTION)
    @JsonbProperty(_RETENTION_ACTION)
    public int getRetentionAction() {
        return _retentionAction;
    }


    @BsonProperty(_RETENTION_ACTION)
    @JsonbProperty(_RETENTION_ACTION)
    public void setRetentionAction(int retentionAction) {
        _retentionAction = retentionAction;
    }


    @BsonProperty(_RETENTION_PERIOD)
    @JsonbProperty(_RETENTION_PERIOD)
    public String getRetentionPeriodStr() {
        return _retentionPeriod;
    }


    @BsonProperty(_RETENTION_PERIOD)
    @JsonbProperty(_RETENTION_PERIOD)
    public void setRetentionPeriodStr(String retentionPeriod) {
        _retentionPeriod = retentionPeriod;
    }


    @BsonProperty(_POST_CREATE_HOOK)
    @JsonbProperty(_POST_CREATE_HOOK)
    public String getPostCreateHook() {
        return _postCreateHook;
    }


    @BsonProperty(_POST_CREATE_HOOK)
    @JsonbProperty(_POST_CREATE_HOOK)
    public void setPostCreateHook(String postCreateHook) {
        _postCreateHook = postCreateHook;
    }


    @Override
    @BsonProperty(_TEMPLATES)
    @JsonbTransient
    public List<TemplateRef> getTemplates() {
        return _templates;
    }


    @Override
    @BsonProperty(_TEMPLATES)
    @JsonbTransient
    public void setTemplates(List<TemplateRef> templateList) {
        _templates.clear();
        _templates.addAll(new LinkedHashSet<>(CollectionUtils.emptyIfNull(templateList))); // Adding only unique items
    }


    @Override
    @BsonProperty(_META)
    @JsonbProperty(_META)
    public Meta getMeta() {
        return _meta;
    }


    @BsonProperty(_META)
    @JsonbProperty(_META)
    public void setMeta(Meta meta) {
        _meta = meta;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public int hashCode() {
        return Objects.hash(
                _id,
                _id_web,
                _name,
                _author,
                _status,
                _type,
                _processIds,
                _categoryId,
                _layoutType,
                _sfDef,
                _cfDef,
                _accessCreate,
                _accessRead,
                _accessReadEditable,
                _accessEditByAdminOnState,
                _accessEditByAuthorOnState,
                _retentionAction,
                _retentionPeriod,
                _postCreateHook,
                _templates,
                _meta);
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FormDefinition that = (FormDefinition) o;
        return _status == that._status
                && _type == that._type
                && _layoutType == that._layoutType
                && _accessReadEditable == that._accessReadEditable
                && _retentionAction == that._retentionAction
                && _accessEditByAdminOnState == that._accessEditByAdminOnState
                && _accessEditByAuthorOnState == that._accessEditByAuthorOnState
                && Objects.equals(_id, that._id)
                && Objects.equals(_id_web, that._id_web)
                && Objects.equals(_name, that._name)
                && Objects.equals(_author, that._author)
                && Objects.equals(_processIds, that._processIds)
                && Objects.equals(_categoryId, that._categoryId)
                && Objects.equals(_sfDef, that._sfDef)
                && Objects.equals(_cfDef, that._cfDef)
                && Objects.equals(_accessCreate, that._accessCreate)
                && Objects.equals(_accessRead, that._accessRead)
                && Objects.equals(_retentionPeriod, that._retentionPeriod)
                && Objects.equals(_postCreateHook, that._postCreateHook)
                && Objects.equals(_templates, that._templates)
                && Objects.equals(_meta, that._meta);
    }
}
