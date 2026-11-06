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
 * DocTopic Standard Field Model
 */
public class StandardFieldTopic implements Serializable {

    @Serial
    private static final long serialVersionUID = -5740795336720016795L;

    private boolean enabled;
    private boolean required;
    private Set<String> tags;


    public StandardFieldTopic() {
        enabled = true;
        required = true;
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


    public Set<String> getTags() {
        return tags;
    }


    public void setTags(Set<String> tags) {
        this.tags = tags;
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        StandardFieldTopic that = (StandardFieldTopic) o;
        return enabled == that.enabled && required == that.required && Objects.equals(tags, that.tags);
    }


    @Override
    public int hashCode() {
        return Objects.hash(enabled, required, tags);
    }
}
