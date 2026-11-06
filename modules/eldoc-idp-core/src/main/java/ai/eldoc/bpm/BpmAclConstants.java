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
package ai.eldoc.bpm;


/**
 * BPM ACL Constants
 */
public class BpmAclConstants {

    // acd - stands for Action CoDe

    public static final String ACTION_DONE = "acd001";
    public static final String ACTION_DONE_W_COMMENTS = "acd002";
    public static final String ACTION_DECLINE = "acd003";

    public static final String ACTION_GET_IN = "acd017";
    public static final String ACTION_DLG = "acd018";
    public static final String ACTION_DLG_CANCEL = "acd019";

    public static final String ACTION_TO_WORK = "acd004";
    public static final String ACTION_TO_WORK_AGAIN = "acd005";

    /**
     * @deprecated to be replaced with ACTION_TO_WORK
     */
    @Deprecated
    public static final String ACTION_TO_APPROVAL = "acd001d";

    /**
     * @deprecated to be replaced with ACTION_TO_WORK
     */
    @Deprecated
    public static final String ACTION_TO_SIGN = "acd002d";

    /**
     * @deprecated to be replaced with ACTION_TO_WORK
     */
    @Deprecated
    public static final String ACTION_TO_EXEC = "acd003d";

    /**
     * @deprecated to be replaced with ACTION_TO_WORK
     */
    @Deprecated
    public static final String ACTION_TO_REPO = "acd004d";

    /**
     * @deprecated to be replaced with ACTION_TO_WORK
     */
    @Deprecated
    public static final String ACTION_TO_ARCH = "acd005d";

    public static final String ACTION_SUSPEND = "acd006";
    public static final String ACTION_RESUME = "acd007";
    public static final String ACTION_EDIT = "acd008";
    public static final String ACTION_SAVE_EDIT = "acd009";
    public static final String ACTION_SAVE_DRAFT = "acd010";
    public static final String ACTION_DELETE = "acd011";
    public static final String ACTION_BLOCK = "acd012";
    public static final String ACTION_UNBLOCK = "acd013";
    public static final String ACTION_TERMINATE = "acd014";
    public static final String ACTION_RESP_ADD = "acd015";
    public static final String ACTION_ONLINE_EDIT = "acd016";
    public static final String ACTION_RETURN_TO_INITIATOR = "acd023";

    public static final String ACTION_REMINDER = "acd020";
    public static final String ACTION_ACC_CONTROL = "acd021";
    public static final String ACC_CONTROL_EDITABLE = "acd022";


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private BpmAclConstants() {
    }

}
