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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Extendable Text properties model
 */
public class TextExtendableProperties implements Serializable {

    private static final long serialVersionUID = 2581543017613677673L;
    private static final Logger LOG = LoggerFactory.getLogger(TextExtendableProperties.class);

    private static final String BOTTOM_ANCHOR_DEFAULT_STR = "(350.0;150.0;100.0;75.0)"; // Should include decimal 0, as serializing i.e. double 150 will give string 150.0

    private static final String TEXT_EXT_BOTTOM_ANCHOR = "txtExtBtmAncr";
    private static final String TEXT_EXT_BOTTOM_ANCHOR_TEXT = "txtExtBtmAncrTxt";
    private static final String TEXT_EXT_BOTTOM_ANCHOR_DISABLED = "txtExtBtmAncrDsbld";

    private static final List<String> PROP_ALL = List.of(TEXT_EXT_BOTTOM_ANCHOR, TEXT_EXT_BOTTOM_ANCHOR_TEXT, TEXT_EXT_BOTTOM_ANCHOR_DISABLED);

    private final PropertyAwareModel _propertyHolder;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public TextExtendableProperties(PropertyAwareModel propertyHolder) {
        _propertyHolder = propertyHolder;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Clears Extendable text related properties
     * IMPORTANT: to be used with care!
     */
    void clear() {
        PROP_ALL.forEach(tableProp -> _propertyHolder.getProperties().remove(tableProp));
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public Rectangle getBottomAnchor() {
        String rectStr = _propertyHolder.getProperty(TEXT_EXT_BOTTOM_ANCHOR, BOTTOM_ANCHOR_DEFAULT_STR);
        try {
            return Rectangle.fromString(rectStr);
        } catch (IllegalArgumentException e1) {
            LOG.warn(rectStr, e1);
            return new Rectangle(350, 150, 150, 75);
        }
    }


    public void setBottomAnchor(Rectangle bottomAnchor) {
        if (bottomAnchor == null) {
            // In case 'null' passed - remove BottomAnchor
            _propertyHolder.removeProperty(TEXT_EXT_BOTTOM_ANCHOR);
            return;
        }
        if (BOTTOM_ANCHOR_DEFAULT_STR.equals(bottomAnchor.toString())) {
            // Do not store default values
            _propertyHolder.removeProperty(TEXT_EXT_BOTTOM_ANCHOR);
            return;
        }
        _propertyHolder.setProperty(TEXT_EXT_BOTTOM_ANCHOR, bottomAnchor.toString());
    }


    public String getBottomAnchorText() {
        return _propertyHolder.getProperty(TEXT_EXT_BOTTOM_ANCHOR_TEXT, "");
    }


    public void setBottomAnchorText(String BottomAnchorText) {
        if (isBlank(BottomAnchorText)) {
            // Do not store default values
            _propertyHolder.removeProperty(TEXT_EXT_BOTTOM_ANCHOR_TEXT);
            return;
        }
        _propertyHolder.setProperty(TEXT_EXT_BOTTOM_ANCHOR_TEXT, BottomAnchorText);
    }


    public boolean isBottomAnchorDisabled() {
        return _propertyHolder.getProperty(TEXT_EXT_BOTTOM_ANCHOR_DISABLED, false);
    }


    public void setBottomAnchorDisabled(boolean disabled) {
        if (disabled == false) {
            // Do not store default values
            _propertyHolder.removeProperty(TEXT_EXT_BOTTOM_ANCHOR_DISABLED);
            return;
        }
        _propertyHolder.setProperty(TEXT_EXT_BOTTOM_ANCHOR_DISABLED, disabled);
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(getBottomAnchor());
        hash = 53 * hash + Objects.hashCode(getBottomAnchorText());
        hash = 53 * hash + Objects.hashCode(isBottomAnchorDisabled());
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
        final TextExtendableProperties other = (TextExtendableProperties) obj;
        if (!Objects.equals(getBottomAnchor(), other.getBottomAnchor())) {
            return false;
        }
        if (!Objects.equals(getBottomAnchorText(), other.getBottomAnchorText())) {
            return false;
        }
        if (!Objects.equals(isBottomAnchorDisabled(), other.isBottomAnchorDisabled())) {
            return false;
        }
        return true;
    }
}
