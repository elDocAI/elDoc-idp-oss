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

import ai.eldoc.model.filemeta.FileMeta;
import ai.eldoc.util.JsonbUtils;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.apache.commons.lang3.tuple.Triple;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;


/**
 * Model represents Recognition Form Result
 */
public class RecoFormResult implements Serializable {

    private static final long serialVersionUID = -1562632528091025054L;

    /**
     * Confidence level to say, that form matched incoming document.
     * For example, if form confidence is 15% and threshold is 20% - form marked as incorrect.
     */
    public static final double FORM_CONFIDENCE_THRESHOLD_INT = 50;

    public static final String _PAGES = "pages";
    public static final String _RECO_FORM = "recoForm";
    public static final String _RECO_FORM_CONFIDENCE = "recoFormConf";

    private List<RecoPageResult> _pages;
    private RecoForm _recoForm;
    private double _recoFormConfidence;
    private String _preprocessedFile;
    private RecoFileMeta _preprocessedFileMeta;
    private List<Triple<String, Integer, FileMeta>> _extraFiles;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoFormResult() {
        _pages = new CopyOnWriteArrayList<>();
        _extraFiles = new ArrayList<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public String toJson() {
        return JsonbUtils.getInstance().toJson(this);
    }


    /**
     * Returns fields from all pages
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public List<RecoFieldResult> getFields() {
        return getPages().stream().flatMap(page -> page.getFields().stream()).collect(Collectors.toList());
    }


    /**
     * Returns RecoField from matched RecoForm, that corresponds to provided RecoFieldResult
     *
     * @param recoFieldResult
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public RecoField getRecoFieldByResult(RecoFieldResult recoFieldResult) {
        RecoField result = null;
        if (recoFieldResult.getRfPageIdx() < getRecoForm().getPages().size() && recoFieldResult.getRfFieldIdx() < getRecoForm().getPages().get(recoFieldResult.getRfPageIdx()).getFields().size()) {
            result = getRecoForm().getPages().get(recoFieldResult.getRfPageIdx()).getFields().get(recoFieldResult.getRfFieldIdx());
        }
        return result;
    }


    /**
     * Returns List of RecoFieldResults, that are related to provided RecoField.<br>
     * There can be several RecoFieldResults in case, if extendable field (i.e. table)
     * was previously matched on multiple pages
     *
     * @param recoField
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public List<RecoFieldResult> getResultByRecoField(RecoField recoField) {
        int pageIdx = -1;
        int fieldIdx = -1;

        // Locate Page index in RecoForm and Field index on proper page for provided RecoField
        for (int i = 0; i < getRecoForm().getPages().size(); i++) {
            fieldIdx = getRecoForm().getPages().get(i).getFields().indexOf(recoField);
            if (fieldIdx != -1) {
                pageIdx = i;
                break;
            }
        }

        if (fieldIdx == -1 || pageIdx == -1) {
            // Field was not found on recoForm
            return new ArrayList<>();
        }

        List<RecoFieldResult> recoFieldResultList = new ArrayList<>();

        // Iterate over all existing RecoFieldResults and collect all, that refer provided RecoField by it's Page and Field index
        for (RecoFieldResult recoResult : getFields()) {
            if (recoResult.getRfPageIdx() == pageIdx && recoResult.getRfFieldIdx() == fieldIdx) {
                recoFieldResultList.add(recoResult);
            }
        }

        return recoFieldResultList;
    }


    //
    // Static Methods -----------------------------------------------------------------------------
    //
    public static RecoFormResult fromJson(String recoFormResultJson) {
        return JsonbUtils.getInstance().fromJson(recoFormResultJson, RecoFormResult.class);
    }


    public static RecoFormResult fromJson(InputStream recoFormResultInputStream) {
        return JsonbUtils.getInstance().fromJson(recoFormResultInputStream, RecoFormResult.class);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns Pages defined on the RecoForm
     *
     * @return
     */
    public List<RecoPageResult> getPages() {
        return _pages;
    }


    public void setPages(List<RecoPageResult> pages) {
        _pages.clear();
        if (pages != null && !pages.isEmpty()) {
            _pages.addAll(pages);
        }
    }


    public RecoForm getRecoForm() {
        return _recoForm;
    }


    public void setRecoForm(RecoForm recoForm) {
        _recoForm = recoForm;
    }


    @BsonProperty(_RECO_FORM_CONFIDENCE)
    @JsonbProperty(_RECO_FORM_CONFIDENCE)
    public double getRecoFormConfidence() {
        return _recoFormConfidence;
    }


    @BsonProperty(_RECO_FORM_CONFIDENCE)
    @JsonbProperty(_RECO_FORM_CONFIDENCE)
    public void setRecoFormConfidence(double recoFormConfidence) {
        _recoFormConfidence = recoFormConfidence;
    }


    @BsonIgnore
    @JsonbTransient
    public String getPreprocessedFile() {
        return _preprocessedFile;
    }


    @BsonIgnore
    @JsonbTransient
    public void setPreprocessedFile(String preprocessedFile) {
        _preprocessedFile = preprocessedFile;
    }


    @BsonIgnore
    @JsonbTransient
    public RecoFileMeta getPreprocessedFileMeta() {
        return _preprocessedFileMeta;
    }


    @BsonIgnore
    @JsonbTransient
    public void setPreprocessedFileMeta(RecoFileMeta preprocessedFileMeta) {
        _preprocessedFileMeta = preprocessedFileMeta;
    }


    @BsonIgnore
    @JsonbTransient
    public List<Triple<String, Integer, FileMeta>> getExtraFiles() {
        return _extraFiles;
    }


    @BsonIgnore
    @JsonbTransient
    public int getFirstPageIdx() {
        return getPages().stream().mapToInt(RecoPageResult::getPageIdx).min().orElse(0);
    }


    @BsonIgnore
    @JsonbTransient
    public int getLastPageIdx() {
        return getPages().stream().mapToInt(RecoPageResult::getPageIdx).max().orElse(getPages().size());
    }


    @Override
    public int hashCode() {
        int hash = 3;
        hash = 61 * hash + Objects.hashCode(_pages);
        hash = 61 * hash + Objects.hashCode(_recoForm);
        hash = 61 * hash + Objects.hashCode(_recoFormConfidence);
        hash = 61 * hash + Objects.hashCode(_preprocessedFile);
        hash = 61 * hash + Objects.hashCode(_preprocessedFileMeta);
        hash = 61 * hash + Objects.hashCode(_extraFiles);
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
        final RecoFormResult other = (RecoFormResult) obj;
        if (Double.doubleToLongBits(_recoFormConfidence) != Double.doubleToLongBits(other._recoFormConfidence)) {
            return false;
        }
        if (!Objects.equals(_pages, other._pages)) {
            return false;
        }
        if (!Objects.equals(_recoForm, other._recoForm)) {
            return false;
        }
        if (!Objects.equals(_preprocessedFile, other._preprocessedFile)) {
            return false;
        }
        if (!Objects.equals(_preprocessedFileMeta, other._preprocessedFileMeta)) {
            return false;
        }
        if (!Objects.equals(_extraFiles, other._extraFiles)) {
            return false;
        }
        return true;
    }

}
