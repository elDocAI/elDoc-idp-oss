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
import java.util.Objects;


/**
 * Represents single recognition document fragment
 */
public class RecognitionMetaFragment implements Serializable {

    private static final long serialVersionUID = -4062916564355070398L;

    private int _pageStart = -1;
    private int _pageEnd = -1;
    private RecoForm _recoForm;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecognitionMetaFragment() {
    }


    /**
     * Creates and initializes document fragment for provided {@link RecognitionMetaDoc}
     *
     * @param pageStart Index of the first page of the fragment, inclusive
     * @param pageEnd Index of the last page of the fragment, inclusive
     * @param recoForm RecoForm, that determines this fragment type/target
     */
    public RecognitionMetaFragment(int pageStart, int pageEnd, RecoForm recoForm) {
        this();
        _pageStart = pageStart;
        _pageEnd = pageEnd;
        _recoForm = recoForm;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     *
     * @param recoMetaPage {@link RecognitionMetaPage} page to be checked
     * @return {@code true}, if provided page is last page of this fragment, or {@code false} otherwise
     */
    public boolean isLastPage(RecognitionMetaPage recoMetaPage) {
        return recoMetaPage.getPageIdx() == getPageEnd();
    }


    /**
     * Checks, if this range is overlapped by provided range (they have page intersection)
     *
     * @param other
     * @return
     */
    public boolean isOverlappedBy(RecognitionMetaFragment other) {
        return getPageStart() <= other.getPageEnd() && other.getPageStart() <= getPageEnd();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     *
     * @return Index of the first page of the fragment, inclusive
     */
    public int getPageStart() {
        return _pageStart;
    }


    /**
     *
     * @param pageStart Index of the first page of the fragment, inclusive
     */
    public void setPageStart(int pageStart) {
        _pageStart = pageStart;
    }


    /**
     *
     * @return Index of the last page of the fragment, inclusive
     */
    public int getPageEnd() {
        return _pageEnd;
    }


    /**
     *
     * @param pageEnd Index of the last page of the fragment, inclusive
     */
    public void setPageEnd(int pageEnd) {
        _pageEnd = pageEnd;
    }


    /**
     *
     * @return RecoForm, that determines this fragment type/target
     */
    public RecoForm getRecoForm() {
        return _recoForm;
    }


    /**
     *
     * @param recoForm RecoForm, that determines this fragment type/target
     */
    public void setRecoForm(RecoForm recoForm) {
        _recoForm = recoForm;
    }


    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + _pageStart;
        hash = 13 * hash + _pageEnd;
        hash = 13 * hash + Objects.hashCode(_recoForm);
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
        final RecognitionMetaFragment other = (RecognitionMetaFragment) obj;
        if (_pageStart != other._pageStart) {
            return false;
        }
        if (_pageEnd != other._pageEnd) {
            return false;
        }
        return Objects.equals(_recoForm, other._recoForm);
    }

}
