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

import java.io.Serial;
import java.io.Serializable;
import java.util.*;


/**
 * Description Standard Field Model
 */
public class StandardFieldDescription implements Serializable {

    @Serial
    private static final long serialVersionUID = 8120852917791411418L;

    private boolean enabled;
    private boolean required;
    private boolean useRTeditor;
    private HashMap<String, String> name;
    private Set<String> tags;


    public StandardFieldDescription() {
        enabled = true;
        required = false;
        useRTeditor = false;
        name = new HashMap<>();
        name.put("uk", "Коментарі");
        name.put("en", "Description");
        tags = new HashSet<>();
    }


    public boolean isEnabled() {
        return enabled;
    }


    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    public boolean isRequired() {
        return required;
    }


    public void setRequired(boolean required) {
        this.required = required;
    }


    public boolean isUseRTeditor() {
        return useRTeditor;
    }


    public void setUseRTeditor(boolean useRTeditor) {
        this.useRTeditor = useRTeditor;
    }


    public HashMap<String, String> getName() {
        return name;
    }


    public void setName(HashMap<String, String> name) {
        this.name = name;
    }


    public Set<String> getTags() {
        return tags;
    }


    public void setTags(Set<String> tags) {
        this.tags = tags;
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        StandardFieldDescription that = (StandardFieldDescription) o;
        return enabled == that.enabled && required == that.required && useRTeditor == that.useRTeditor && Objects.equals(name, that.name) && Objects.equals(tags, that.tags);
    }


    @Override
    public int hashCode() {
        return Objects.hash(enabled, required, useRTeditor, name, tags);
    }
}
