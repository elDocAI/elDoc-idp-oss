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


/**
 * OMR Properties model
 */
public class OmrProperties implements Serializable {

    private static final long serialVersionUID = -621195888973438212L;

    //public static final int OPMARK_TYPE_AUTO = 0; // Reserved -> to be considered
    public static final int OPMARK_TYPE_BOX = 1;
    public static final int OPMARK_TYPE_CIRCLE = 2;

    public static final int OPMARK_TYPE_DEFAULT = OPMARK_TYPE_BOX;

    private static final String OPMARK_TYPE = "opMarkType";

    private static final List<String> PROP_ALL = Arrays.asList(OPMARK_TYPE);

    private final PropertyAwareModel _recoField;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public OmrProperties(PropertyAwareModel recoField) {
        _recoField = recoField;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Clears table related properties
     * IMPORTANT: to be used with care!
     */
    void clear() {
        PROP_ALL.forEach(omrProp -> _recoField.getProperties().remove(omrProp));
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public int getOpMarkType() {
        return _recoField.getProperty(OPMARK_TYPE, OPMARK_TYPE_DEFAULT);
    }


    public void setOpMarkType(Integer opMarkType) {
        if (opMarkType == null) {
            // Do not store default values
            _recoField.removeProperty(OPMARK_TYPE);
            return;
        }
        if (opMarkType == OPMARK_TYPE_DEFAULT) {
            // Do not store default values
            _recoField.removeProperty(OPMARK_TYPE);
            return;
        }
        _recoField.setProperty(OPMARK_TYPE, opMarkType);
    }


    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(getOpMarkType());
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
        final OmrProperties other = (OmrProperties) obj;
        if (!Objects.equals(getOpMarkType(), other.getOpMarkType())) {
            return false;
        }
        return true;
    }

}
