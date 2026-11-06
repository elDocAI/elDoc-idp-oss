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

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Model represents Master Form Page recognition result
 */
public class RecoPageResult implements Serializable {

    private static final long serialVersionUID = 2534258737418033288L;

    public static final String _FIELDS = "fields";
    public static final String _PAGE_IDX = "pageIdx";

    private List<RecoFieldResult> _fields;
    private int _pageIdx;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * RecoPageResult Model internal constructor, mostly used for Jsonb deserialization
     */
    protected RecoPageResult() {
        _fields = new CopyOnWriteArrayList<>();
    }


    public RecoPageResult(int pageIdx) {
        this();
        _pageIdx = pageIdx;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public List<RecoFieldResult> getFields() {
        return _fields;
    }


    public void setFields(List<RecoFieldResult> fields) {
        _fields.clear();
        if (fields != null && !fields.isEmpty()) {
            _fields.addAll(fields);
        }
    }


    public int getPageIdx() {
        return _pageIdx;
    }


    public void setPageIdx(int pageIdx) {
        _pageIdx = pageIdx;
    }


    @Override
    public int hashCode() {
        int hash = 3;
        hash = 73 * hash + Objects.hashCode(_fields);
        hash = 73 * hash + _pageIdx;
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
        final RecoPageResult other = (RecoPageResult) obj;
        if (!Objects.equals(_fields, other._fields)) {
            return false;
        }
        if (!Objects.equals(_pageIdx, other._pageIdx)) {
            return false;
        }
        return true;
    }

}
