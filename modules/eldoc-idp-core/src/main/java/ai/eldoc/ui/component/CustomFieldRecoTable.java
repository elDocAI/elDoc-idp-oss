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
 * Reco Table Custom Field backing component class
 */
@FacesComponent("cfRecoTable")
public class CustomFieldRecoTable extends UIInput implements NamingContainer {

    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Adds row to the table
     *
     * @param cf RecoTable CustomField, for which the row is added
     * @param cfData CustomField data
     * @param cfName Name of the CustomField
     */
    public void addRowToRecoTable(CustomField cf, Map<String, Object> cfData, String cfName) {
        // Get current CustomField value
        List<List<String>> recoTable = ObjectValueUtils.getValueAsListOfStringList(cfData.get(cfName));
        List<List<List<Integer>>> recoTableConf = cf.getRecoProp().getConfidenceTable();

        // Add row to current CustomField value containing same number of cells, as columns defined in CustomField
        List<String> newRow = new ArrayList<>();
        List<List<Integer>> newRowConf = new ArrayList<>();
        cf.getFields().forEach(column -> {
            newRow.add("");
            List<Integer> cellConf = new ArrayList<>();
            cellConf.add(100);
            newRowConf.add(cellConf);
        });
        recoTable.add(newRow);
        recoTableConf.add(newRowConf);
        cfData.put(cfName, recoTable);
    }


    /**
     * Removes row from the table
     *
     * @param cf RecoTable CustomField, for which the row is removed
     * @param cfData CustomField data
     * @param cfName Name of the CustomField
     * @param rowIdx Index of the row to remove
     */
    public void removeRowFromRecoTable(CustomField cf, Map<String, Object> cfData, String cfName, int rowIdx) {
        // Get current CustomField value
        List<List<String>> recoTable = ObjectValueUtils.getValueAsListOfStringList(cfData.get(cfName));
        List<List<List<Integer>>> recoTableConf = cf.getRecoProp().getConfidenceTable();
        recoTable.remove(rowIdx);
        recoTableConf.remove(rowIdx);
        cfData.put(cfName, recoTable);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    public String getFamily() {
        return UINamingContainer.COMPONENT_FAMILY;
    }
}
