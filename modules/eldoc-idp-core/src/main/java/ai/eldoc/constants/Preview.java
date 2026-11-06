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
package ai.eldoc.constants;


/**
 * Preview Constants
 */
public final class Preview {

    public static final int TYPE_NONE = 0;
    public static final int TYPE_NATIVE = 5;
    public static final int TYPE_DOC_AS_IMG = 10;
    public static final int TYPE_DOC_AS_PDF = 20;
    public static final int TYPE_DOC_ONLINE_VIEW = 30;
    public static final int TYPE_DOC_ONLINE_EDIT = 40;          // review mode is optional
    public static final int TYPE_DOC_ONLINE_EDIT_REVIEW = 50;   // review mode is forced

}
