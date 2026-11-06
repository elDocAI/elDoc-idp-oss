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
package ai.eldoc.model.reco;

import ai.eldoc.annotation.RestApiVisible;
import ai.eldoc.api.RestApiVisibilityStrategy;
import ai.eldoc.model.*;
import ai.eldoc.util.JsonbUtils;
import jakarta.json.bind.annotation.*;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import static java.text.MessageFormat.format;


/**
 * Workflow Recognition Data holder Model
 */
@JsonbVisibility(RestApiVisibilityStrategy.class) // when private property and public getter with the same name require special handling
public class RecoData implements BasicMetaAwareModel, BasicFileAwareModel, CustomFieldAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = 3563793641949833805L;
    private static final Logger LOG = LoggerFactory.getLogger(RecoData.class);

    public static final String ATTACHMENT_RECORESULT = "recoResult.json"; // in RecoWorkflow (Workflow of fdr1 type)


    /**
     * Tags for tagging file-attachments
     *
     * @since 5.2.0
     */
    public enum FILE_TAG {
        IDP_PREPROCESSED_FINAL, // Preprocessed file by specific form or plugin (shown on UI by default if present). Before v5.2.0 was stored within Workflows
        IDP_RECORESULT, // Recognition results. Before v5.2.0 was stored within Workflows
        IDP_FIELD_ATTACHMENT // Field attachment (for example, image for Image-type fields)
    }

    public static final String _REF_ID = "refId"; // reference to the parent document id
    public static final String _RECO_FORM = "recoForm";
    public static final String _PAGE_IDX_START = "pageIdxStart";
    public static final String _PAGE_IDX_END = "pageIdxEnd";
    public static final String _VALIDATED = "validated"; // Means either data was auto-validated or manually validated via UI

    private String _id;
    private String _refId;
    private Meta _meta;
    private List<DbFileRef> _files;
    private List<CustomField> _cfDef;
    private Map<String, Object> _cfData;
    private RecoFormDto _recoForm;
    private int _pageIdxStart;
    private int _pageIdxEnd;
    private boolean _validated;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * RecoData Model internal constructor, mostly used for Jsonb deserialization
     */
    protected RecoData() {
        _meta = new Meta();
        _files = new ArrayList<>();
        _cfDef = new ArrayList<>();
        _cfData = new HashMap<>();
    }


    public RecoData(Workflow parentWorkflow) {
        this();
        _refId = parentWorkflow.getId();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @BsonIgnore
    @JsonbTransient
    public boolean isRecoFormAltered(RecoFormResult recoFormResult) {
        if (getRecoForm() == null && recoFormResult == null) {
            // In case both not set - treat it as not altered (scenario: no form matched and no form set manually)
            return false;
        }

        if (getRecoForm() != null && recoFormResult == null) {
            // This is manual validation scenario: no form was matched by RecoBot but it is manually set by Validator
            return true;
        }

        if (getRecoForm() == null && recoFormResult != null) {
            // In case RecoForm is not defined but RecoFormResult is present - treat it as altered (THIS CASE SHOULD NEVER HAPPEN)
            LOG.warn("RecoData[{}] isRecoFormAltered() check got the wrong pair of values: recoForm is NULL and recoFormResult is NOT NULL", getId());
            return true;
        }
        return !Objects.equals(getRecoForm().getName(), (recoFormResult.getRecoForm() == null ? null : recoFormResult.getRecoForm().getName()));
    }


    /**
     * Returns RecoData Title used on UI, etc
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public String getRecoDataTitle() {
        return format("{0} ({1}){2}",
                getRecoForm().getName(),
                (getPageIdxStart() == getPageIdxEnd()
                        ? format("p{0,number,#}", getPageIdxStart() + 1)
                        : format("p{0,number,#}-{1,number,#}", getPageIdxStart() + 1, getPageIdxEnd() + 1)),
                isValidated() ? "" : " [Requires validation]"
        );
    }


    public String toJson() {
        return JsonbUtils.getInstance().toJson(this);
    }


    public static RecoData fromJson(String recoDataJsonStr) {
        return JsonbUtils.getInstance().fromJson(recoDataJsonStr, RecoData.class);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @RestApiVisible
    @JsonbProperty(_REF_ID)
    public String getRefId() {
        return _refId;
    }


    @JsonbProperty(_REF_ID)
    public void setRefId(String refId) {
        _refId = refId;
    }


    @RestApiVisible
    @JsonbProperty(_RECO_FORM)
    public RecoFormDto getRecoForm() {
        return _recoForm;
    }


    @JsonbProperty(_RECO_FORM)
    public void setRecoForm(RecoFormDto recoForm) {
        _recoForm = recoForm;
    }


    /**
     * Start page (inclusive)
     *
     * @return
     */
    @RestApiVisible
    @JsonbProperty(_PAGE_IDX_START)
    public int getPageIdxStart() {
        return _pageIdxStart;
    }


    /**
     * Start page (inclusive)
     *
     * @param startPageIdx
     */
    @JsonbProperty(_PAGE_IDX_START)
    public void setPageIdxStart(int startPageIdx) {
        _pageIdxStart = startPageIdx;
    }


    /**
     * End page (inclusive)
     *
     * @return
     */
    @RestApiVisible
    @JsonbProperty(_PAGE_IDX_END)
    public int getPageIdxEnd() {
        return _pageIdxEnd;
    }


    /**
     * End page (inclusive)
     *
     * @param endPageIdx
     */
    @JsonbProperty(_PAGE_IDX_END)
    public void setPageIdxEnd(int endPageIdx) {
        _pageIdxEnd = endPageIdx;
    }


    /**
     * Returns flag that this RecoData was validated (either auto or manually)<br/>
     * In order to check whether this RecoData was auto-validated or validated manually you should
     * use code-snippet like below in order to check that at least one filed confidence was below threshold:<br/>
     * <pre>
     * <code>
     * for (int pageIndex = 0; pageIndex {@literal <} recoFormResult.getPages().size(); pageIndex++) {
     *     RecoPageResult recoPageResult = recoFormResult.getPages().get(pageIndex);
     *     for (int fieldIndex = 0; fieldIndex {@literal <} recoPageResult.getFields().size(); fieldIndex++) {
     *         recoFieldResult.getConfidenceMin() {@literal <} recoField.getConfidenceThreshold()
     *     }
     * }
     * </code>
     * </pre>
     *
     * @return
     */
    @RestApiVisible
    @JsonbProperty(_VALIDATED)
    public boolean isValidated() {
        return _validated;
    }


    /**
     * Sets flag that this RecoData was validated (either auto or manually)
     *
     * @param validated
     */
    @JsonbProperty(_VALIDATED)
    public void setValidated(boolean validated) {
        _validated = validated;
    }


    //
    // Getters & Setters overrides ----------------------------------------------------------------
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


    @Override
    @RestApiVisible
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
    @RestApiVisible
    @BsonProperty(_FILES)
    @JsonbProperty(_FILES)
    public List<DbFileRef> getFiles() {
        return _files;
    }


    @BsonProperty(_FILES)
    @JsonbProperty(_FILES)
    public void setFiles(List<DbFileRef> files) {
        _files = files;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_CF_DEF)
    @JsonbProperty(_CF_DEF)
    public List<CustomField> getCfDef() {
        return _cfDef;
    }


    /**
     * Required for de-serialization
     *
     * @param cfDef
     */
    @BsonProperty(_CF_DEF)
    @JsonbProperty(_CF_DEF)
    public void setCfDef(List<CustomField> cfDef) {
        _cfDef = cfDef;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_CF_DATA)
    @JsonbProperty(_CF_DATA)
    public Map<String, Object> getCfData() {
        return _cfData;
    }


    /**
     * Required for de-serialization
     *
     * @param cfData
     */
    @BsonProperty(_CF_DATA)
    @JsonbProperty(_CF_DATA)
    public void setCfData(Map<String, Object> cfData) {
        _cfData = cfData;
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 11 * hash + Objects.hashCode(_id);
        hash = 11 * hash + Objects.hashCode(_refId);
        hash = 11 * hash + Objects.hashCode(_meta);
        hash = 11 * hash + Objects.hashCode(_files);
        hash = 11 * hash + Objects.hashCode(_cfDef);
        hash = 11 * hash + Objects.hashCode(_cfData);
        hash = 11 * hash + Objects.hashCode(_recoForm);
        hash = 11 * hash + _pageIdxStart;
        hash = 11 * hash + _pageIdxEnd;
        hash = 11 * hash + (_validated ? 1 : 0);
        return hash;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RecoData other = (RecoData) obj;
        if (_pageIdxStart != other._pageIdxStart) {
            return false;
        }
        if (_pageIdxEnd != other._pageIdxEnd) {
            return false;
        }
        if (_validated != other._validated) {
            return false;
        }
        if (!Objects.equals(_id, other._id)) {
            return false;
        }
        if (!Objects.equals(_refId, other._refId)) {
            return false;
        }
        if (!Objects.equals(_meta, other._meta)) {
            return false;
        }
        if (!Objects.equals(_files, other._files)) {
            return false;
        }
        if (!Objects.equals(_cfDef, other._cfDef)) {
            return false;
        }
        if (!Objects.equals(_cfData, other._cfData)) {
            return false;
        }
        if (!Objects.equals(_recoForm, other._recoForm)) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return "RecoData{_id=" + _id + "; refId=" + _refId + "; recoForm=" + _recoForm + '}';
    }

}
