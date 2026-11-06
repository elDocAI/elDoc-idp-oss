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
package ai.eldoc.model;

import ai.eldoc.Util;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Interface defines CustomField Aware Model
 */
public interface CustomFieldAwareModel {

    public static final String _CF_DEF = "_cfDef";
    public static final String _CF_DEF_JSON = "cfDef";
    public static final String _CF_DATA = "_cfData";
    public static final String _CF_DATA_JSON = "cfData";


    //
    // Methods definition -------------------------------------------------------------------------
    //
    default public CustomField getCfByTagsFirst(Set<String> tags) {
        return getCfDef().stream().filter(customField -> Util.hasIntersection(customField.getTags(), tags)).findFirst().orElse(null);
    }


    default public List<CustomField> getCfByTags(Set<String> tags) {
        return getCfDef().stream().filter(customField -> Util.hasIntersection(customField.getTags(), tags)).collect(Collectors.toList());
    }


    /**
     * Returns Custom Field Data Source key name where its value is stored.<br>
     * WARNING: contains a major flaw, see the deprecated description area
     *
     * @param customField
     * @return
     * @deprecated It is not really deprecated but marked like this for now to attract attention as it contains major flaw: CF with all equal params
     * including field names (what should not be a case but we do not validate it) will give the same index, as there is nothing to differentiate them
     * by the equals() method. As such this method can give the same fieldName for the CFs with the different indexes.
     */
    @Deprecated
    default public String getCfDsFieldName(CustomField customField) {
        return customField.getDSFieldName(getCfDef().indexOf(customField));
    }


    /**
     * Deletes all Custom Fields definitions and assigned Data
     */
    default public void cfAndDataClear() {
        getCfDef().clear();
        getCfData().clear();
    }


    /**
     * Creates a new Custom Field definition and adds it to the Custom Fields definitions list
     *
     * @return Newly created and added Custom Field
     */
    default public CustomField cfCreateAndAdd() {
        CustomField cf = new CustomField();
        getCfDef().add(cf);
        return cf;
    }


    /**
     * Deletes Custom Field by the given index
     *
     * @param index Custom Field index
     */
    default public void cfDelete(int index) {
        getCfDef().remove(index);
    }


    /**
     * Deletes Custom Field by the given reference
     *
     * @param customField Custom Field reference
     */
    default public void cfDelete(CustomField customField) {
        getCfDef().remove(customField);
    }


    //
    // Getters ------------------------------------------------------------------------------------
    //

    /**
     * @return CustomFields Definitions
     */
    public List<CustomField> getCfDef();


    /**
     * @return CustomFields values
     */
    public Map<String, Object> getCfData();

}
