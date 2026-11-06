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
import ai.eldoc.annotation.RestApiVisible;
import ai.eldoc.api.RestApiVisibilityStrategy;
import ai.eldoc.bpm.*;
import ai.eldoc.bpm.Process;
import jakarta.json.bind.annotation.*;
import org.apache.commons.lang3.StringUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.jspecify.annotations.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import static ai.eldoc.bpm.BpmConstants.STATE_ID_ARCH;


/**
 * Workflow Model<br/>
 * It serves as replacement for the Workflow. We are starting with signature workflow first and in future all Workflows will be migrated to the Workflow POJO Model
 */
@I18nSupport(fields = {Workflow._NAME})
@JsonbVisibility(RestApiVisibilityStrategy.class) // when private property and public getter with the same name require special handling
public class Workflow implements BasicFileAwareModel, FileTemplateAwareModel, BasicMetaAwareModel, BasicAclAwareModel, BasicAuthorAwareModel, PropertyAwareModel, TagAwareModel, CustomFieldAwareModel, BasicTenantIdAwareModel,
        ProcessableDocument, Serializable {

    @Serial
    private static final long serialVersionUID = -8978105899631793654L;

    public static final String _FORM = "frm"; // Workflow form id
    public static final String _FORM_JSON = "form";
    public static final String _TYPE = "typ"; // Workflow form type: 0 - static, 1 - dynamic. Matches to the field Workflow.formsystype
    public static final String _TYPE_JSON = "type";
    public static final String _NAME = "nm"; // Map contaning human-friendly name of the workflow per language, e.g.: Invoice, Contract, etc. Matches to doctitle[_en|_uk|_ru] in Workflow
    public static final String _NAME_JSON = "name";
    public static final String _AUTHOR_BY = "authorBy"; // Author system id who actually created the workflow
    public static final String _AUTHOR_BY_JSON = _AUTHOR_BY;
    public static final String _STATE = "stt";
    public static final String _STATE_JSON = "state";
    public static final String _STEP = "stp";
    public static final String _STEP_JSON = "step";
    public static final String _ITERATION = "itr";
    public static final String _ITERATION_JSON = "iteration";
    public static final String _STATUS = "stts";
    public static final String _STATUS_JSON = "status";
    public static final String _PROCESS = "prcs";
    public static final String _PROCESS_JSON = "process";
    public static final String _PROCESS_ID = "prcsId";
    public static final String _PROCESS_ID_JSON = "processId";
    public static final String _PROCESS_NAME = "prcsNm";
    public static final String _PROCESS_NAME_JSON = "processName";
    public static final String _DELEGATED_TO = "dlgTo"; // Also affects ACL and participates in BPM part
    public static final String _DELEGATED_TO_JSON = "delegatedTo";
    public static final String _SF_DEF = "_sfDef"; // Standard fields definition
    public static final String _SF_DEF_JSON = "sfDef";
    public static final String _NUMBER = "num"; // Workflow number
    public static final String _NUMBER_JSON = "number";
    public static final String _TOPIC = "topic"; // Workflow topic
    public static final String _TOPIC_JSON = _TOPIC;
    public static final String _NOTE = "note"; // Workflow note
    public static final String _NOTE_JSON = _NOTE;
    public static final String _DATE_CREATED = "dtCrtd";
    public static final String _DATE_CREATED_JSON = "dateCreated";
    public static final String _DATE_RECEIVED = "dtRcvd";
    public static final String _DATE_RECEIVED_JSON = "dateReceived";
    public static final String _DATE_DUE = "dtDue";
    public static final String _DATE_DUE_JSON = "dateDue";
    public static final String _DATE_COMPLETED = "dtCmpltd";
    public static final String _DATE_COMPLETED_JSON = "dateCompleted";
    public static final String _DATE_LAST_ACTIVITY = "dtLstAct";
    public static final String _DATE_LAST_ACTIVITY_JSON = "dateLastActivity";
    public static final String _REF_ITEMS = "ref"; // Refernces to the other workflow entries
    public static final String _REF_ITEMS_JSON = "references";
    public static final String _LAYOUT_TYPE = "lytTyp"; // Form Layout Type: 0 - default, 1 - N - other types
    public static final String _LAYOUT_TYPE_JSON = "layoutType";
    public static final String _TEMPLATES = FileTemplateAwareModel._TEMPLATES;
    public static final String _TEMPLATES_JSON = "templates";


    public enum FILE_TAG {
        /**
         * Signature document source file
         */
        SGN_SOURCE,
        /**
         * Signature document file, prepared for signing
         */
        SGN_PRPD,
        /**
         * Signature document file, signed by user
         */
        SGN_SIGNED,
        /**
         * Signature document report
         */
        SGN_REPORT,
        /**
         * Combined version of signature document files (includes all files, attached to document and signed by user, plus report file)
         */
        SGN_BUNDLE,
        /**
         * Signature document response attachment to field (Field type: File)
         */
        SGN_FF,
        /**
         * IDP Source file
         */
        IDP_SOURCE,
        /**
         * IDP Preprocessed file by system with default approach (shown on the UI if IDP_PREPROCESSED_FINAL is not present)
         */
        IDP_PREPROCESSED
    }

    private String _id;
    private String _idWeb; // Not used in Global collections
    private String _idExt; // Not used in Global collections
    private String _author;
    private String _authorBy;
    private String _form;
    private int _type;
    private Map<String, String> _name;
    private Process _process;
    private String _processId;
    private String _processName;
    private int _status;
    private int _state;
    private int _step;
    private int _iteration;
    private String _number;
    private String _topic;
    private String _note;
    private Date _dateCreated;
    private Date _dateReceived;
    private Date _dateDue;
    private Date _dateCompleted;
    private Date _dateLastActivity;
    private StandardFieldDef _sfDef;
    private List<CustomField> _cfDef;
    private Map<String, Object> _cfData;
    private Meta _meta;
    private Acl _acl;
    private List<DbFileRef> _files;
    private List<TemplateRef> _templates;
    private Map<String, Object> _properties;
    private List<String> _tags;
    private List<String> _ref;
    private List<String> _delegatedTo; // NOTE: adding as is using old naming convention (all lowercase), to be refactored during migration of Workflows to Workflow
    private int _layoutType;
    private String _tid;


    //
    // Constructor --------------------------------------------------------------------------------
    //


    /**
     * Workflow Model internal constructor, mostly used for MongoDB POJO support only
     */
    protected Workflow() {
        this(null, null, null);
    }


    public Workflow(String authorSystemId, Set<String> writeAccess, Set<String> readAccess) {
        // Init own properties
        _name = new HashMap<>();
        _sfDef = new StandardFieldDef();
        _cfDef = new ArrayList<>();
        _cfData = new HashMap<>();
        _meta = new Meta();
        _acl = new Acl();
        _files = new ArrayList<>();
        _templates = new ArrayList<>();
        _properties = new TreeMap<>();
        _ref = new ArrayList<>();
        _delegatedTo = new ArrayList<>();

        // Set values
        _author = authorSystemId;
        if (writeAccess != null) {
            _acl.getW().addAll(writeAccess);
        }
        if (readAccess != null) {
            _acl.getR().addAll(readAccess);
        }

        // Init fields
        setDateCreated(new Date());
        setType(FormDefinition.TYPE_DYNAMIC);
        _tid = null;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    //
    // Getters & Setters (virtual) ----------------------------------------------------------------
    //


    /**
     * Checks if the given name is author of the Workflow
     *
     * @param name
     * @return
     */
    @BsonIgnore
    public boolean isAuthor(String name) {
        return StringUtils.equals(getAuthor(), name);
    }


    /**
     * Returns workflow name for the given lang key
     *
     * @param langCode
     * @return
     */
    @BsonIgnore
    public String getNameByLang(String langCode) {
        return _name.getOrDefault(langCode, "Not set for lang: [" + langCode + "]");
    }


    /**
     * Sets workflow name for the given lang key
     *
     * @param langCode
     * @param value in case value is {@code NULL} or empty - removes give langCode, otherwise sets the give value
     */
    @BsonIgnore
    public void setNameByLang(String langCode, String value) {
        if (value == null || value.isBlank()) {
            _name.remove(langCode);
        } else {
            _name.put(langCode, value);
        }
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeDinamic() {
        return getType() == FormDefinition.TYPE_DYNAMIC;
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeStatic() {
        return getType() == FormDefinition.TYPE_STATIC;
    }


    /**
     * Adds delegation systemId
     *
     * @param systemId
     */
    public void addDelegatedUser(final String systemId) {
        if (!_delegatedTo.contains(systemId)) {
            _delegatedTo.add(systemId);
        }
    }


    /**
     * Retrieves configuration of the requested process state
     *
     * @param stateNum
     * @return
     */
    @BsonIgnore
    public ProcessState getProcessState(Integer stateNum) {
        return getProcess().getState(stateNum);
    }


    /**
     * Returns current ProcessState model of the Workflow
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public ProcessState getProcessState() {
        return getProcess().getState(getState());
    }


    /**
     * Retrieves all names of the ProcessState
     *
     * @param stateNum
     * @param inclAuthor
     * @param inclGroupMembers
     * @return
     */
    @BsonIgnore
    public List<String> getProcessStateNamesAll(Integer stateNum, Boolean inclAuthor, Boolean inclGroupMembers) {
        return getProcessState(stateNum).getNamesAll(inclAuthor, inclGroupMembers);
    }


    /**
     * Returns stateId of the next Process State
     *
     * @return stateId of the next Process State or -1 in case there is the last Process State already
     */
    @BsonIgnore
    @JsonbTransient
    public int getNextState() {
        int currentState = getState();

        if (STATE_ID_ARCH == currentState) {
            return -1;
        }

        List<Integer> stateOrder = getProcess().getStateIdOrder();
        int currentStateIndex = stateOrder.indexOf(currentState);
        if (0 <= currentStateIndex && currentStateIndex < (stateOrder.size() - 1)) {
            return stateOrder.get(currentStateIndex + 1);
        } else {
            return -1;
        }
    }


    //
    // Reco logic related methods -----------------------------------------------------------------
    //


    /**
     * Indicates that this Workflow is a Reco Workflow (Workflow of form like 'frd1')
     */
    @BsonIgnore
    public boolean isRecoWF() {
        return FormDefinition.isRecoWFForm(getForm());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    @RestApiVisible
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


    /**
     * NOTE: Not used in Global collections
     *
     * @return Workflow idWeb
     */
    @RestApiVisible
    @BsonProperty(_ID_WEB)
    @JsonbProperty(_ID_WEB)
    public String getIdWeb() {
        return _idWeb;
    }


    /**
     * NOTE: Not used in Global collections
     *
     * @param idWeb Workflow idWeb
     */
    @BsonProperty(_ID_WEB)
    @JsonbProperty(_ID_WEB)
    public void setIdWeb(String idWeb) {
        _idWeb = idWeb;
    }


    @BsonProperty(_ID_EXT)
    @JsonbProperty(_ID_EXT)
    public String getIdExt() {
        return _idExt;
    }


    @BsonProperty(_ID_EXT)
    @JsonbProperty(_ID_EXT)
    public void setIdExt(String idExt) {
        _idExt = idExt;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_AUTHOR)
    @JsonbProperty(_AUTHOR_JSON)
    public String getAuthor() {
        return _author;
    }


    @BsonProperty(_AUTHOR)
    @JsonbProperty(_AUTHOR_JSON)
    public void setAuthor(String author) {
        _author = author;
    }


    @RestApiVisible
    @BsonProperty(_AUTHOR_BY)
    @JsonbProperty(_AUTHOR_BY_JSON)
    public String getAuthorBy() {
        return _authorBy;
    }


    @BsonProperty(_AUTHOR_BY)
    @JsonbProperty(_AUTHOR_BY_JSON)
    public void setAuthorBy(String authorBy) {
        _authorBy = authorBy;
    }


    @RestApiVisible
    @BsonProperty(_FORM)
    @JsonbProperty(_FORM_JSON)
    public String getForm() {
        return _form;
    }


    @BsonProperty(_FORM)
    @JsonbProperty(_FORM_JSON)
    public void setForm(String form) {
        _form = form;
    }


    @RestApiVisible
    @BsonProperty(_TYPE)
    @JsonbProperty(_TYPE_JSON)
    public int getType() {
        return _type;
    }


    @BsonProperty(_TYPE)
    @JsonbProperty(_TYPE_JSON)
    public void setType(int type) {
        _type = type;
    }


    @RestApiVisible
    @BsonProperty(_NAME)
    @JsonbProperty(_NAME_JSON)
    public Map<String, String> getName() {
        return _name;
    }


    @BsonProperty(_NAME)
    @JsonbProperty(_NAME_JSON)
    public void setName(Map<String, String> name) {
        _name = name;
    }


    @Override
    @BsonProperty(_PROCESS)
    @JsonbProperty(_PROCESS_JSON)
    public Process getProcess() {
        return _process;
    }


    @BsonProperty(_PROCESS)
    @JsonbProperty(_PROCESS_JSON)
    public void setProcess(Process process) {
        _process = process;
    }


    @Override
    @BsonProperty(_PROCESS_ID)
    @JsonbProperty(_PROCESS_ID_JSON)
    public String getProcessId() {
        return _processId;
    }


    @BsonProperty(_PROCESS_ID)
    @JsonbProperty(_PROCESS_ID_JSON)
    public void setProcessId(String processId) {
        _processId = processId;
    }


    @BsonProperty(_PROCESS_NAME)
    @JsonbProperty(_PROCESS_NAME_JSON)
    public String getProcessName() {
        return _processName;
    }


    @BsonProperty(_PROCESS_NAME)
    @JsonbProperty(_PROCESS_NAME_JSON)
    public void setProcessName(String processName) {
        _processName = processName;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_STATUS)
    @JsonbProperty(_STATUS_JSON)
    public int getStatus() {
        return _status;
    }


    @BsonProperty(_STATUS)
    @JsonbProperty(_STATUS_JSON)
    public void setStatus(int status) {
        _status = status;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_STATE)
    @JsonbProperty(_STATE_JSON)
    public int getState() {
        return _state;
    }


    @BsonProperty(_STATE)
    @JsonbProperty(_STATE_JSON)
    public void setState(int state) {
        _state = state;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_STEP)
    @JsonbProperty(_STEP_JSON)
    public int getStep() {
        return _step;
    }


    @BsonProperty(_STEP)
    @JsonbProperty(_STEP_JSON)
    public void setStep(int step) {
        _step = step;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_ITERATION)
    @JsonbProperty(_ITERATION_JSON)
    public int getIteration() {
        return _iteration;
    }


    @BsonProperty(_ITERATION)
    @JsonbProperty(_ITERATION_JSON)
    public void setIteration(int iteration) {
        _iteration = iteration;
    }


    /**
     * @return workflow form number value
     */
    @RestApiVisible
    @BsonProperty(_NUMBER)
    @JsonbProperty(_NUMBER_JSON)
    public String getNum() {
        return _number;
    }


    /**
     * @param number workflow form number value
     */
    @BsonProperty(_NUMBER)
    @JsonbProperty(_NUMBER_JSON)
    public void setNum(String number) {
        _number = number;
    }


    @RestApiVisible
    @BsonProperty(_TOPIC)
    @JsonbProperty(_TOPIC_JSON)
    public String getTopic() {
        return _topic;
    }


    @BsonProperty(_TOPIC)
    @JsonbProperty(_TOPIC_JSON)
    public void setTopic(String topic) {
        _topic = topic;
    }


    @RestApiVisible
    @BsonProperty(_NOTE)
    @JsonbProperty(_NOTE_JSON)
    public String getNote() {
        return _note;
    }


    @BsonProperty(_NOTE)
    @JsonbProperty(_NOTE_JSON)
    public void setNote(String note) {
        _note = note;
    }


    @RestApiVisible
    @BsonProperty(_DATE_CREATED)
    @JsonbProperty(_DATE_CREATED_JSON)
    public Date getDateCreated() {
        return _dateCreated;
    }


    @BsonProperty(_DATE_CREATED)
    @JsonbProperty(_DATE_CREATED_JSON)
    public void setDateCreated(Date dateCreated) {
        _dateCreated = dateCreated;
    }


    @RestApiVisible
    @BsonProperty(_DATE_RECEIVED)
    @JsonbProperty(_DATE_RECEIVED_JSON)
    public Date getDateReceived() {
        return _dateReceived;
    }


    @BsonProperty(_DATE_RECEIVED)
    @JsonbProperty(_DATE_RECEIVED_JSON)
    public void setDateReceived(Date dateReceived) {
        _dateReceived = dateReceived;
    }


    @RestApiVisible
    @BsonProperty(_DATE_DUE)
    @JsonbProperty(_DATE_DUE_JSON)
    public Date getDateDue() {
        return _dateDue;
    }


    @BsonProperty(_DATE_DUE)
    @JsonbProperty(_DATE_DUE_JSON)
    public void setDateDue(Date dateDue) {
        _dateDue = dateDue;
    }


    @RestApiVisible
    @BsonProperty(_DATE_COMPLETED)
    @JsonbProperty(_DATE_COMPLETED_JSON)
    public Date getDateCompleted() {
        return _dateCompleted;
    }


    @BsonProperty(_DATE_COMPLETED)
    @JsonbProperty(_DATE_COMPLETED_JSON)
    public void setDateCompleted(Date dateCompleted) {
        _dateCompleted = dateCompleted;
    }


    /**
     * Transient (calculated in runtime) property
     *
     * @return
     */
    @BsonProperty(_DATE_LAST_ACTIVITY)
    @JsonbProperty(_DATE_LAST_ACTIVITY_JSON)
    public Date getDateLastActivity() {
        return _dateLastActivity;
    }


    @BsonProperty(_DATE_LAST_ACTIVITY)
    @JsonbProperty(_DATE_LAST_ACTIVITY_JSON)
    public void setDateLastActivity(Date dateLastActivity) {
        _dateLastActivity = dateLastActivity;
    }


    @RestApiVisible
    @BsonProperty(_SF_DEF)
    @JsonbProperty(_SF_DEF_JSON)
    public StandardFieldDef getSfDef() {
        return _sfDef;
    }


    @BsonProperty(_SF_DEF)
    @JsonbProperty(_SF_DEF_JSON)
    public void setSfDef(StandardFieldDef sfDef) {
        _sfDef = sfDef;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_CF_DEF)
    @JsonbProperty(_CF_DEF_JSON)
    public List<CustomField> getCfDef() {
        return _cfDef;
    }


    /**
     * Required for deserialization
     *
     * @param cfDef CustomFields definition
     */
    @BsonProperty(_CF_DEF)
    @JsonbProperty(_CF_DEF_JSON)
    public void setCfDef(List<CustomField> cfDef) {
        _cfDef = cfDef;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_CF_DATA)
    @JsonbProperty(_CF_DATA_JSON)
    public Map<String, Object> getCfData() {
        return _cfData;
    }


    /**
     * Required for deserialization
     *
     * @param cfData CustomFields values
     */
    @BsonProperty(_CF_DATA)
    @JsonbProperty(_CF_DATA_JSON)
    public void setCfData(Map<String, Object> cfData) {
        _cfData = cfData;
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


    @Override
    @BsonProperty(_ACL)
    @JsonbProperty(_ACL)
    public Acl getAcl() {
        return _acl;
    }


    @BsonProperty(_ACL)
    @JsonbProperty(_ACL)
    public void setAcl(Acl acl) {
        _acl = acl;
    }


    @Override
    @BsonProperty(_FILES)
    @JsonbTransient
    public List<DbFileRef> getFiles() {
        return _files;
    }


    @BsonProperty(_FILES)
    @JsonbTransient
    public void setFiles(List<DbFileRef> files) {
        _files.clear();
        if (files != null && !files.isEmpty()) {
            _files.addAll(files);
        }
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
    public void setTemplates(List<TemplateRef> templates) {
        _templates.clear();
        if (templates != null && !templates.isEmpty()) {
            _templates.addAll(templates);
        }
    }


    @Override
    @BsonProperty(_PROPERTIES)
    @JsonbProperty(_PROPERTIES)
    public Map<String, Object> getProperties() {
        return _properties;
    }


    @BsonProperty(_PROPERTIES)
    @JsonbProperty(_PROPERTIES)
    public void setProperties(Map<String, Object> properties) {
        _properties.clear();
        if (properties != null && !properties.isEmpty()) {
            _properties.putAll(properties);
        }
    }


    @Override
    @Nullable
    @RestApiVisible
    @BsonProperty(_TAGS)
    @JsonbProperty(_TAGS_JSON)
    public List<String> getTags() {
        return _tags;
    }


    @RestApiVisible(writable = true)
    @Override
    @BsonProperty(_TAGS)
    @JsonbProperty(_TAGS_JSON)
    public void setTags(@Nullable List<String> tags) {
        _tags = normalizeTags(tags);
    }


    @RestApiVisible
    @BsonProperty(_REF_ITEMS)
    @JsonbProperty(_REF_ITEMS_JSON)
    public List<String> getRefItems() {
        return _ref;
    }


    @BsonProperty(_REF_ITEMS)
    @JsonbProperty(_REF_ITEMS_JSON)
    public void setRefItems(List<String> ref) {
        _ref.clear();
        if (ref != null && !ref.isEmpty()) {
            _ref.addAll(new HashSet<>(ref)); // We need to leave only unique entries
        }
    }


    @BsonProperty(_DELEGATED_TO)
    @JsonbProperty(_DELEGATED_TO_JSON)
    public List<String> getDelegatedTo() {
        return _delegatedTo;
    }


    @BsonProperty(_DELEGATED_TO)
    @JsonbProperty(_DELEGATED_TO_JSON)
    public void setDelegatedTo(List<String> delegatedTo) {
        _delegatedTo = delegatedTo;
    }


    @BsonProperty(_LAYOUT_TYPE)
    @JsonbProperty(_LAYOUT_TYPE_JSON)
    public int getLayoutType() {
        return _layoutType;
    }


    @BsonProperty(_LAYOUT_TYPE)
    @JsonbProperty(_LAYOUT_TYPE_JSON)
    public void setLayoutType(int layoutType) {
        _layoutType = layoutType;
    }


    /**
     * {@inheritDoc}
     */
    @BsonProperty(_TID)
    @JsonbProperty(_TID)
    @Override
    public String getTenantId() {
        return _tid;
    }


    @BsonProperty(_TID)
    @JsonbProperty(_TID)
    public void setTenantId(String tid) {
        _tid = tid;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Workflow workflow = (Workflow) o;
        return _type == workflow._type && _status == workflow._status && _state == workflow._state && _step == workflow._step && _iteration == workflow._iteration && _layoutType == workflow._layoutType
                && Objects.equals(_id, workflow._id) && Objects.equals(_idWeb, workflow._idWeb) && Objects.equals(_idExt, workflow._idExt) && Objects.equals(_author, workflow._author) && Objects.equals(_authorBy, workflow._authorBy)
                && Objects.equals(_form, workflow._form) && Objects.equals(_name, workflow._name) && Objects.equals(_process, workflow._process) && Objects.equals(_processId, workflow._processId)
                && Objects.equals(_processName, workflow._processName) && Objects.equals(_number, workflow._number) && Objects.equals(_topic, workflow._topic) && Objects.equals(_note, workflow._note)
                && Objects.equals(_dateCreated, workflow._dateCreated) && Objects.equals(_dateReceived, workflow._dateReceived) && Objects.equals(_dateDue, workflow._dateDue) && Objects.equals(_dateCompleted, workflow._dateCompleted)
                && Objects.equals(_dateLastActivity, workflow._dateLastActivity) && Objects.equals(_sfDef, workflow._sfDef) && Objects.equals(_cfDef, workflow._cfDef) && Objects.equals(_cfData, workflow._cfData)
                && Objects.equals(_meta, workflow._meta) && Objects.equals(_acl, workflow._acl) && Objects.equals(_files, workflow._files) && Objects.equals(_templates, workflow._templates)
                && Objects.equals(_properties, workflow._properties) && Objects.equals(_tags, workflow._tags) && Objects.equals(_ref, workflow._ref)
                && Objects.equals(_delegatedTo, workflow._delegatedTo) && Objects.equals(_tid, workflow._tid);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_id, _idWeb, _idExt, _author, _authorBy, _form, _type, _name, _process, _processId, _processName, _status, _state, _step, _iteration, _number, _topic, _note, _dateCreated, _dateReceived,
                _dateDue, _dateCompleted, _dateLastActivity, _sfDef, _cfDef, _cfData, _meta, _acl, _files, _templates, _properties, _tags, _ref, _delegatedTo, _layoutType, _tid);
    }
}
