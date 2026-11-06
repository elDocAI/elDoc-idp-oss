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
package ai.eldoc.ui.component;

import ai.eldoc.model.CustomField;
import ai.eldoc.util.ObjectValueUtils;
import jakarta.faces.component.*;
import java.util.*;


/**
 * Table Custom Field backing component class
 */
@FacesComponent("cfTable")
public class CustomFieldTable extends UIInput implements NamingContainer {

    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Adds row to the table, populated with empty cells
     *
     * @param cf Table CustomField, for which the row is added
     * @param cfData CustomField data
     * @param cfName Name of the CustomField
     */
    public void addRowToTable(CustomField cf, Map<String, Object> cfData, String cfName) {
        // Get current CustomField value
        List<List<String>> table = ObjectValueUtils.getValueAsListOfStringList(cfData.get(cfName));

        // Add row to current CustomField value containing same number of cells, as columns defined in CustomField
        List<String> newRow = new ArrayList<>(Collections.nCopies(cf.getFields().size(), ""));
        table.add(newRow);
        cfData.put(cfName, table);
    }


    /**
     * Removes row from the table
     *
     * @param cfData CustomField data
     * @param cfName Name of the CustomField
     * @param rowIdx Index of the row to remove
     */
    public void removeRowFromTable(Map<String, Object> cfData, String cfName, int rowIdx) {
        // Get current CustomField value
        List<List<String>> table = ObjectValueUtils.getValueAsListOfStringList(cfData.get(cfName));
        table.remove(rowIdx);
        cfData.put(cfName, table);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    public String getFamily() {
        return UINamingContainer.COMPONENT_FAMILY;
    }
}
