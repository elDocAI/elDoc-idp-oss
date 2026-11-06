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


/**
 * Represents single recognition document's page
 */
public class RecognitionMetaPage implements Serializable {

    private static final long serialVersionUID = 5017276245859414516L;

    private final int _pageIdx;
    private boolean _isMachineGeneratedPage;


    public RecognitionMetaPage(int pageIdx) {
        _pageIdx = pageIdx;
    }


    public int getPageIdx() {
        return _pageIdx;
    }


    public boolean isMachineGeneratedPage() {
        return _isMachineGeneratedPage;
    }


    public void setIsMachineGeneratedPage(boolean isMachineGeneratedPage) {
        _isMachineGeneratedPage = isMachineGeneratedPage;
    }


    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + _pageIdx;
        hash = 29 * hash + (_isMachineGeneratedPage ? 1 : 0);
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
        final RecognitionMetaPage other = (RecognitionMetaPage) obj;
        if (_pageIdx != other._pageIdx) {
            return false;
        }
        if (_isMachineGeneratedPage != other._isMachineGeneratedPage) {
            return false;
        }
        return true;
    }

}
