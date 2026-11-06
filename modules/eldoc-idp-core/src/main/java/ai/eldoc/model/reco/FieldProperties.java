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
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Recognition Field settings (general recoField properties)
 */
public class FieldProperties implements Serializable {

    private static final long serialVersionUID = -3664477544803936001L;

    private final static String FLD_USE_POSTPROC = "fldUsePostProc";
    private final static String FLD_POSTPROC = "fldPostProc";
    private final static String FLD_IGNORED = "fldIgnored";
    private final static String FLD_AUTO = "fldAuto";
    private final static String FLD_AI_DESCRIPTION = "aiDsc";

    private static final List<String> PROP_ALL = Arrays.asList(
            FLD_USE_POSTPROC,
            FLD_POSTPROC,
            FLD_IGNORED,
            FLD_AUTO,
            FLD_AI_DESCRIPTION
    );

    private final PropertyAwareModel _recoField;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public FieldProperties(PropertyAwareModel recoField) {
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
        PROP_ALL.forEach(propName -> _recoField.getProperties().remove(propName));
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public boolean isUsePostProc() {
        return _recoField.getProperty(FLD_USE_POSTPROC, false);
    }


    public void setUsePostProc(boolean usePostProc) {
        if (usePostProc == false) {
            // Do not store default values
            _recoField.removeProperty(FLD_USE_POSTPROC);
            return;
        }
        _recoField.setProperty(FLD_USE_POSTPROC, usePostProc);
    }


    public boolean isIgnored() {
        return _recoField.getProperty(FLD_IGNORED, false);
    }


    public void setIgnored(boolean fldIgnore) {
        if (fldIgnore == false) {
            // Do not store default values
            _recoField.removeProperty(FLD_IGNORED);
            return;
        }
        _recoField.setProperty(FLD_IGNORED, fldIgnore);
    }


    public String getPostProc() {
        return _recoField.getProperty(FLD_POSTPROC, "");
    }


    /**
     * Checks, if field is not ignored.<br>
     * Required for UI only
     *
     * @return
     */
    public boolean isNotIgnored() {
        return !isIgnored();
    }


    /**
     * Marks this field as not ignored<br>
     * Required for UI only
     *
     * @param enabled
     */
    public void setNotIgnored(boolean enabled) {
        setIgnored(!enabled);
    }


    public void setPostProc(String postProcCode) {
        if (isBlank(postProcCode)) {
            // Do not store default values
            _recoField.removeProperty(FLD_POSTPROC);
            return;
        }
        _recoField.setProperty(FLD_POSTPROC, postProcCode);
    }


    public String getPostProcDecoded() throws ParseException {
        if (isBlank(getPostProc())) {
            return "";
        }
        return new String(Base64.getDecoder().decode(getPostProc()), StandardCharsets.UTF_8);
    }


    public boolean isAuto() {
        return _recoField.getProperty(FLD_AUTO, false);
    }


    public void setAuto(boolean autoFld) {
        if (autoFld == false) {
            // Do not store default values
            _recoField.removeProperty(FLD_AUTO);
            return;
        }
        _recoField.setProperty(FLD_AUTO, autoFld);
    }


    public String getAiDescription() {
        return _recoField.getProperty(FLD_AI_DESCRIPTION, "");
    }


    public void setAiDescription(String description) {
        if (isBlank(description)) {
            // Do not store default values
            _recoField.removeProperty(FLD_AI_DESCRIPTION);
            return;
        }
        _recoField.setProperty(FLD_AI_DESCRIPTION, description);
    }


    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Objects.hashCode(getPostProc());
        hash = 59 * hash + Objects.hashCode(isIgnored());
        hash = 59 * hash + Objects.hashCode(isUsePostProc());
        hash = 59 * hash + Objects.hashCode(isAuto());
        hash = 59 * hash + Objects.hashCode(getAiDescription());
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
        final FieldProperties other = (FieldProperties) obj;
        if (!Objects.equals(getPostProc(), other.getPostProc())) {
            return false;
        }
        if (!Objects.equals(isIgnored(), other.isIgnored())) {
            return false;
        }
        if (!Objects.equals(isUsePostProc(), other.isUsePostProc())) {
            return false;
        }
        if (!Objects.equals(isAuto(), other.isAuto())) {
            return false;
        }
        if (!Objects.equals(getAiDescription(), other.getAiDescription())) {
            return false;
        }
        return true;
    }

}
