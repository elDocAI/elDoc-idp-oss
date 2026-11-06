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
import java.util.*;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Table Properties model
 */
public class TableProperties implements Serializable {

    private static final long serialVersionUID = -1603664115517020877L;
    private static final Logger LOG = LoggerFactory.getLogger(TableProperties.class);

    private static final String ANCHOR_UNDER_TABLE_DEFAULT_STR = "(350.0;150.0;150.0;75.0)"; // Should include decimal 0, as serializing i.e. double 150 will give string 150.0

    @Deprecated
    private static final String TABLE_ROW_SEPARATOR = "tblRowSeparator"; // Not used and going to be removed
    @Deprecated
    private static final String TABLE_COL_SEPARATOR = "tblColSeparator"; // Not used and going to be removed
    private static final String TABLE_HEADER_REPEATED = "tblHeaderRepeated";
    private static final String TABLE_ANCHOR_UNDER_TABLE_TEXT = "tblAnchorUnderTableText";
    private final static String TABLE_ANCHOR_UNDER_TABLE = "tblAnchorUnderTable";
    private final static String TABLE_ANCHOR_UNDER_TABLE_PAGE_IDX = "tblAnchorUnderTablePgIdx";
    private static final String TABLE_ANCHOR_UNDER_TABLE_DISABLED = "tblAnchorUnderTableDisabled";

    private static final List<String> PROP_ALL = Arrays.asList(TABLE_ROW_SEPARATOR, TABLE_COL_SEPARATOR, TABLE_HEADER_REPEATED,
            TABLE_ANCHOR_UNDER_TABLE_TEXT, TABLE_ANCHOR_UNDER_TABLE, TABLE_ANCHOR_UNDER_TABLE_PAGE_IDX, TABLE_ANCHOR_UNDER_TABLE_DISABLED);

    private final PropertyAwareModel _propertyHolder;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public TableProperties(PropertyAwareModel propertyHolder) {
        _propertyHolder = propertyHolder;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Clears table related properties
     * IMPORTANT: to be used with care!
     */
    void clear() {
        PROP_ALL.forEach(tableProp -> _propertyHolder.getProperties().remove(tableProp));
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public boolean isRowSeparator() {
        return _propertyHolder.getProperty(TABLE_ROW_SEPARATOR, false);
    }


    public void setRowSeparator(boolean rowSeparator) {
        if (rowSeparator == false) {
            // Do not store default values
            _propertyHolder.removeProperty(TABLE_ROW_SEPARATOR);
            return;
        }
        _propertyHolder.setProperty(TABLE_ROW_SEPARATOR, rowSeparator);
    }


    public boolean isColSeparator() {
        return _propertyHolder.getProperty(TABLE_COL_SEPARATOR, false);
    }


    public void setColSeparator(boolean colSeparator) {
        if (colSeparator == false) {
            // Do not store default values
            _propertyHolder.removeProperty(TABLE_COL_SEPARATOR);
            return;
        }
        _propertyHolder.setProperty(TABLE_COL_SEPARATOR, colSeparator);
    }


    public boolean isHeaderRepeated() {
        return _propertyHolder.getProperty(TABLE_HEADER_REPEATED, false);
    }


    public void setHeaderRepeated(boolean headerRepeated) {
        if (headerRepeated == false) {
            // Do not store default values
            _propertyHolder.removeProperty(TABLE_HEADER_REPEATED);
            return;
        }
        _propertyHolder.setProperty(TABLE_HEADER_REPEATED, headerRepeated);
    }


    public String getAnchorUnderTableText() {
        return _propertyHolder.getProperty(TABLE_ANCHOR_UNDER_TABLE_TEXT, "");
    }


    public void setAnchorUnderTableText(String tableAnchorUnderTableText) {
        if (isBlank(tableAnchorUnderTableText)) {
            // Do not store default values
            _propertyHolder.removeProperty(TABLE_ANCHOR_UNDER_TABLE_TEXT);
            return;
        }
        _propertyHolder.setProperty(TABLE_ANCHOR_UNDER_TABLE_TEXT, tableAnchorUnderTableText);
    }


    public Rectangle getAnchorUnderTable() {
        String rectStr = _propertyHolder.getProperty(TABLE_ANCHOR_UNDER_TABLE, ANCHOR_UNDER_TABLE_DEFAULT_STR);
        try {
            return Rectangle.fromString(rectStr);
        } catch (IllegalArgumentException e1) {
            LOG.warn(rectStr, e1);
            return new Rectangle(300, 0, 300, 150);
        }
    }


    public void setAnchorUnderTable(Rectangle anchorUnderTable) {
        if (anchorUnderTable == null) {
            // In case 'null' passed - remove AnchorUnderTable
            _propertyHolder.removeProperty(TABLE_ANCHOR_UNDER_TABLE);
            return;
        }
        if (ANCHOR_UNDER_TABLE_DEFAULT_STR.equals(anchorUnderTable.toString())) {
            // Do not store default values
            _propertyHolder.removeProperty(TABLE_ANCHOR_UNDER_TABLE);
            return;
        }
        _propertyHolder.setProperty(TABLE_ANCHOR_UNDER_TABLE, anchorUnderTable.toString());
    }


    /**
     * Returns page index, where AnchorUnderTable is located.<br>
     * Default value is 0, that means, that AnchorUnderTable is on same page, as field itself.
     *
     * @return
     */
    public int getAnchorUnderTablePageIndex() {
        return _propertyHolder.getProperty(TABLE_ANCHOR_UNDER_TABLE_PAGE_IDX, 0);
    }


    /**
     * Sets AnchorUnderTable page index to provided<br>
     * Default value is 0, that means, that AnchorUnderTable is on same page, as field itself.
     *
     * @param anchorUnderTablePageIndex
     */
    public void setAnchorUnderTablePageIndex(int anchorUnderTablePageIndex) {
        if (anchorUnderTablePageIndex == 0) {
            // In case '0' passed - remove AnchorUnderTablePgIdx
            _propertyHolder.removeProperty(TABLE_ANCHOR_UNDER_TABLE_PAGE_IDX);
            return;
        }
        _propertyHolder.setProperty(TABLE_ANCHOR_UNDER_TABLE_PAGE_IDX, anchorUnderTablePageIndex);
    }



    public boolean isAnchorUnderTableDisabled() {
        return _propertyHolder.getProperty(TABLE_ANCHOR_UNDER_TABLE_DISABLED, false);
    }


    public void setAnchorUnderTableDisabled(boolean disabled) {
        if (disabled == false) {
            // Do not store default values
            _propertyHolder.removeProperty(TABLE_ANCHOR_UNDER_TABLE_DISABLED);
            return;
        }
        _propertyHolder.setProperty(TABLE_ANCHOR_UNDER_TABLE_DISABLED, disabled);
    }


    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(getAnchorUnderTable());
        hash = 53 * hash + Objects.hashCode(getAnchorUnderTablePageIndex());
        hash = 53 * hash + Objects.hashCode(getAnchorUnderTableText());
        hash = 53 * hash + Objects.hashCode(isColSeparator());
        hash = 53 * hash + Objects.hashCode(isHeaderRepeated());
        hash = 53 * hash + Objects.hashCode(isRowSeparator());
        hash = 53 * hash + Objects.hashCode(isAnchorUnderTableDisabled());
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
        final TableProperties other = (TableProperties) obj;
        if (!Objects.equals(getAnchorUnderTable(), other.getAnchorUnderTable())) {
            return false;
        }
        if (!Objects.equals(getAnchorUnderTablePageIndex(), other.getAnchorUnderTablePageIndex())) {
            return false;
        }
        if (!Objects.equals(getAnchorUnderTableText(), other.getAnchorUnderTableText())) {
            return false;
        }
        if (!Objects.equals(isColSeparator(), other.isColSeparator())) {
            return false;
        }
        if (!Objects.equals(isHeaderRepeated(), other.isHeaderRepeated())) {
            return false;
        }
        if (!Objects.equals(isRowSeparator(), other.isRowSeparator())) {
            return false;
        }
        if (!Objects.equals(isAnchorUnderTableDisabled(), other.isAnchorUnderTableDisabled())) {
            return false;
        }
        return true;
    }

}
