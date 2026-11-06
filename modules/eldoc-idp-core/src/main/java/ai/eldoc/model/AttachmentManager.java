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

import ai.eldoc.constants.Preview;
import java.io.Serializable;


/**
 * Attachment Manager settings model
 */
public class AttachmentManager implements Serializable {

    private static final long serialVersionUID = 144984880366450746L;

    private boolean allowTemplatesDelete = true;
    private boolean allowDownload = true;
    private int previewType = Preview.TYPE_DOC_AS_PDF; // Keep it by default AS_PDF, never set to EDIT_ONLINE as default mode (as for example for integration vch it is not allowed)


    public boolean isAllowTemplatesDelete() {
        return allowTemplatesDelete;
    }


    public void setAllowTemplatesDelete(boolean allowTemplatesDelete) {
        this.allowTemplatesDelete = allowTemplatesDelete;
    }


    public boolean isAllowDownload() {
        return allowDownload;
    }


    public void setAllowDownload(boolean allowDownload) {
        this.allowDownload = allowDownload;
    }


    public int getPreviewType() {
        return previewType;
    }


    public void setPreviewType(int previewType) {
        this.previewType = previewType;
    }


    public boolean isAllowPreview() {
        switch (previewType) {
            case Preview.TYPE_NATIVE:
            case Preview.TYPE_DOC_AS_IMG:
            case Preview.TYPE_DOC_AS_PDF:
            case Preview.TYPE_DOC_ONLINE_VIEW:
                return true;
        }
        return false;
    }


    public boolean isAllowEdit() {
        switch (previewType) {
            case Preview.TYPE_DOC_ONLINE_EDIT:
            case Preview.TYPE_DOC_ONLINE_EDIT_REVIEW:
                return true;
        }
        return false;
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + (this.allowTemplatesDelete ? 1 : 0);
        hash = 83 * hash + (this.allowDownload ? 1 : 0);
        hash = 83 * hash + this.previewType;
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
        final AttachmentManager other = (AttachmentManager) obj;
        if (this.allowTemplatesDelete != other.allowTemplatesDelete) {
            return false;
        }
        if (this.allowDownload != other.allowDownload) {
            return false;
        }
        return this.previewType == other.previewType;
    }

}
