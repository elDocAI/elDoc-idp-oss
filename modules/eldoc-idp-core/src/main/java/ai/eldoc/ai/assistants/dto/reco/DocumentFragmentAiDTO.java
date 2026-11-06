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
package ai.eldoc.ai.assistants.dto.reco;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * LLM DTO for describing document fragment during file classification/segmentation
 */
public class DocumentFragmentAiDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = -516235523406169026L;

    private int _pageStart;
    private int _pageEnd;
    private int _confidence;
    private String _note;
    private List<String> _candidates;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public DocumentFragmentAiDTO() {
        _candidates = new ArrayList<>();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public int getPageStart() {
        return _pageStart;
    }


    public void setPageStart(int pageStart) {
        _pageStart = pageStart;
    }


    public int getPageEnd() {
        return _pageEnd;
    }


    public void setPageEnd(int pageEnd) {
        _pageEnd = pageEnd;
    }


    public List<String> getCandidates() {
        return _candidates;
    }


    public void setCandidates(List<String> candidates) {
        _candidates = candidates;
    }


    public int getConfidence() {
        return _confidence;
    }


    public void setConfidence(int confidence) {
        _confidence = confidence;
    }


    public String getNote() {
        return _note;
    }


    public void setNote(String note) {
        _note = note;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DocumentFragmentAiDTO that = (DocumentFragmentAiDTO) o;
        return _pageStart == that._pageStart
                && _pageEnd == that._pageEnd
                && _confidence == that._confidence
                && Objects.equals(_note, that._note)
                && Objects.equals(_candidates, that._candidates);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_pageStart, _pageEnd, _confidence, _note, _candidates);
    }
}
