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
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * DocNum Standard Field Model
 */
public class StandardFieldNum implements Serializable {

    @Serial
    private static final long serialVersionUID = 8099719612933102196L;

    private boolean enabled;
    private boolean required;
    private boolean autoReg;
    private Integer autoRegState;
    private Set<String> tags;


    public StandardFieldNum() {
        enabled = false;
        required = false;
        autoReg = false;
        autoRegState = 0;
        tags = new HashSet<>();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public boolean isDisabled(final Workflow workflow, final boolean isEditable) {
        if (!isEditable) {
            return true;
        }
        return autoReg;
    }


    public boolean isRendered(final Workflow workflow) {
        boolean isRendered;
        if (autoReg) {
            isRendered = isNotBlank(workflow.getNum());
        } else {
            isRendered = enabled;
        }
        return isRendered;
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


    public boolean isAutoReg() {
        return autoReg;
    }


    public void setAutoReg(boolean autoReg) {
        this.autoReg = autoReg;
        if (this.autoReg) {
            this.enabled = true;
            this.required = true;
        } else {
            this.enabled = false;
            this.required = false;
        }
    }


    public Integer getAutoRegState() {
        return autoRegState;
    }


    public void setAutoRegState(Integer autoRegState) {
        this.autoRegState = autoRegState;
    }


    public Set<String> getTags() {
        return tags;
    }


    public void setTags(Set<String> tags) {
        this.tags = tags;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        return Objects.hash(enabled,
                required,
                autoReg,
                autoRegState,
                tags);
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        StandardFieldNum that = (StandardFieldNum) o;
        return enabled == that.enabled
                && required == that.required
                && autoReg == that.autoReg
                && Objects.equals(autoRegState, that.autoRegState)
                && Objects.equals(tags, that.tags);
    }
}
