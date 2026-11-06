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

import ai.eldoc.reco.RecoDebugger;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Represents single recognition document, it's metadata and matching/processing results
 */
public class RecognitionMetaDoc implements Serializable {

    private static final long serialVersionUID = -4844184149684090767L;

    private final List<RecognitionMetaPage> _recoMetaPageList;
    private final List<RecognitionMetaFragment> _recoMetaFragmentList;

    private final List<RecoFormResult> _recoResultList;
    private final RecoDebugger _recoDebugger;
    private int _currentPageIdx;

    //
    // Constructors -------------------------------------------------------------------------------
    //
    public RecognitionMetaDoc(RecoFileMeta recoFileMeta) {
        _recoMetaPageList = new ArrayList<>();
        _recoMetaFragmentList = new ArrayList<>();
        _recoResultList = new ArrayList<>();
        _recoDebugger = new RecoDebugger();

        for (int i = 0; i < recoFileMeta.getPageCount(); i++) {
            _recoMetaPageList.add(new RecognitionMetaPage(i));
        }

        _currentPageIdx = 0;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public int getCurrentPageIdx() {
        return _currentPageIdx;
    }


    public void setCurrentPageIdx(int currentPageIdx) {
        _currentPageIdx = currentPageIdx;
    }


    public RecognitionMetaPage getCurrentPage() {
        return _recoMetaPageList.get(getCurrentPageIdx());
    }


    /**
     *
     * @return List of {@link RecognitionMetaFragment}s, that start with current page
     */
    public List<RecognitionMetaFragment> getCurrentPageFragments() {
        return getRecoMetaFragmentList().stream()
                .filter(fragment -> fragment.getPageStart() == getCurrentPageIdx())
                .collect(Collectors.toList());
    }


    public int getNextDocumentStartPageIdx() {
        return getNextDocumentStartPageIdx(getCurrentPageIdx());
    }


    public int getNextDocumentStartPageIdx(int currentPage) {
        return getRecoMetaFragmentList().stream()
                .mapToInt(RecognitionMetaFragment::getPageStart)
                .filter(i -> i > currentPage)
                .min().orElse(getNumberOfPages());

    }


    public List<RecognitionMetaPage> getRecoMetaPageList() {
        return _recoMetaPageList;
    }


    public List<RecognitionMetaFragment> getRecoMetaFragmentList() {
        return _recoMetaFragmentList;
    }


    public List<RecoFormResult> getRecoResultList() {
        return _recoResultList;
    }


    public RecoDebugger getRecoDebugger() {
        return _recoDebugger;
    }


    public boolean isDocumentEndReached() {
        return getCurrentPageIdx() >= getNumberOfPages();
    }


    public int getNumberOfPages() {
        return _recoMetaPageList.size();
    }


    @Override
    public int hashCode() {
        int hash = 5;
        hash = 17 * hash + Objects.hashCode(_recoMetaPageList);
        hash = 17 * hash + Objects.hashCode(_recoMetaFragmentList);
        hash = 17 * hash + Objects.hashCode(_recoResultList);
        hash = 17 * hash + Objects.hashCode(_recoDebugger);
        hash = 17 * hash + _currentPageIdx;
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
        final RecognitionMetaDoc other = (RecognitionMetaDoc) obj;
        if (_currentPageIdx != other._currentPageIdx) {
            return false;
        }
        if (!Objects.equals(_recoMetaPageList, other._recoMetaPageList)) {
            return false;
        }
        if (!Objects.equals(_recoMetaFragmentList, other._recoMetaFragmentList)) {
            return false;
        }
        if (!Objects.equals(_recoDebugger, other._recoDebugger)) {
            return false;
        }
        if (!Objects.equals(_recoResultList, other._recoResultList)) {
            return false;
        }
        return true;
    }

}
