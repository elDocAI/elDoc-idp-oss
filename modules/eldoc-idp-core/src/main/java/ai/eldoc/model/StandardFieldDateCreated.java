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
 * DateCreated Standard Field Model
 */
public class StandardFieldDateCreated implements Serializable {

    @Serial
    private static final long serialVersionUID = 2712656643123513424L;

    private boolean enabled;
    private boolean editable;
    private Set<String> tags;


    public StandardFieldDateCreated() {
        enabled = true;
        editable = false;
        tags = new HashSet<>();
    }


    public boolean isEnabled() {
        return enabled;
    }


    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    public boolean isEditable() {
        return editable;
    }


    public void setEditable(boolean editable) {
        this.editable = editable;
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
        StandardFieldDateCreated that = (StandardFieldDateCreated) o;
        return enabled == that.enabled && editable == that.editable && Objects.equals(tags, that.tags);
    }


    @Override
    public int hashCode() {
        return Objects.hash(enabled, editable, tags);
    }
}
