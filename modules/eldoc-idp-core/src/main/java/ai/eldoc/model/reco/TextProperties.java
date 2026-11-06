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

import ai.eldoc.model.PropertyAwareModel;
import java.io.Serializable;
import java.util.*;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Recognition Text Field settings
 */
public class TextProperties implements Serializable {

    private static final long serialVersionUID = 1766116891348959958L;

    private final static String TEXT_USE_REGEX = "txtUseRegex";
    private final static String TEXT_REGEX_TYPE = "txtRegexType";
    private final static String TEXT_REGEX = "txtRegex";
    private final static String TEXT_REGEX_BITAP_MSTKCNT = "txtRegexBtpMstCnt";
    private final static String TEXT_DATE_TIME_FORMAT = "txtDateTimeFormat";

    public final static int TYPE_STANDARD = 0;
    public final static int TYPE_BITAP = 1;

    private static final List<String> PROP_ALL = Arrays.asList(
            TEXT_USE_REGEX,
            TEXT_REGEX_TYPE,
            TEXT_REGEX,
            TEXT_REGEX_BITAP_MSTKCNT,
            TEXT_DATE_TIME_FORMAT
    );

    private final PropertyAwareModel _recoField;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public TextProperties(PropertyAwareModel recoField) {
        _recoField = recoField;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Clears text related properties
     * IMPORTANT: to be used with care!
     */
    void clear() {
        PROP_ALL.forEach(tableProp -> _recoField.getProperties().remove(tableProp));
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public boolean isUseRegex() {
        return _recoField.getProperty(TEXT_USE_REGEX, false);
    }


    public void setUseRegex(boolean useRegex) {
        if (useRegex == false) {
            // Do not store default values
            _recoField.removeProperty(TEXT_USE_REGEX);
            return;
        }
        _recoField.setProperty(TEXT_USE_REGEX, useRegex);
    }


    public int getRegexType() {
        return _recoField.getProperty(TEXT_REGEX_TYPE, 0);
    }


    public void setRegexType(int regexType) {
        if (regexType == 0) {
            // Do not store default values
            _recoField.removeProperty(TEXT_REGEX_TYPE);
            return;
        }
        _recoField.setProperty(TEXT_REGEX_TYPE, regexType);
    }


    public String getRegex() {
        return _recoField.getProperty(TEXT_REGEX, "");
    }


    public void setRegex(String regexPattern) {
        if (isBlank(regexPattern)) {
            // Do not store default values
            _recoField.removeProperty(TEXT_REGEX);
            return;
        }
        _recoField.setProperty(TEXT_REGEX, regexPattern);
    }


    public int getRegexBitapMistakesCount() {
        return _recoField.getProperty(TEXT_REGEX_BITAP_MSTKCNT, 0);
    }


    public void setRegexBitapMistakesCount(int mstkCount) {
        if (mstkCount == 0) {
            // Do not store default values
            _recoField.removeProperty(TEXT_REGEX_BITAP_MSTKCNT);
            return;
        }
        _recoField.setProperty(TEXT_REGEX_BITAP_MSTKCNT, mstkCount);
    }


    public String getDateTimeFormat() {
        return _recoField.getProperty(TEXT_DATE_TIME_FORMAT, "");
    }


    public void setDateTimeFormat(String format) {
        if (isBlank(format)) {
            // Do not store default values
            _recoField.removeProperty(TEXT_DATE_TIME_FORMAT);
            return;
        }
        _recoField.setProperty(TEXT_DATE_TIME_FORMAT, format);
    }


    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Objects.hashCode(getDateTimeFormat());
        hash = 59 * hash + Objects.hashCode(getRegex());
        hash = 59 * hash + Objects.hashCode(getRegexBitapMistakesCount());
        hash = 59 * hash + Objects.hashCode(getRegexType());
        hash = 59 * hash + Objects.hashCode(isUseRegex());
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
        final TextProperties other = (TextProperties) obj;
        if (!Objects.equals(getDateTimeFormat(), other.getDateTimeFormat())) {
            return false;
        }
        if (!Objects.equals(getRegex(), other.getRegex())) {
            return false;
        }
        if (!Objects.equals(getRegexBitapMistakesCount(), other.getRegexBitapMistakesCount())) {
            return false;
        }
        if (!Objects.equals(getRegexType(), other.getRegexType())) {
            return false;
        }
        if (!Objects.equals(isUseRegex(), other.isUseRegex())) {
            return false;
        }
        return true;
    }

}
