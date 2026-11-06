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

import org.bson.codecs.pojo.annotations.BsonProperty;


/**
 * Interface declares Basic ACL Aware Model
 */
public interface BasicAclAwareModel extends BasicModel {

    public static final String _ACL = "_acl";
    public static final String _PATH_ACL_W = _ACL + "." + Acl._W;
    public static final String _PATH_ACL_WH = _ACL + "." + Acl._WH;
    public static final String _PATH_ACL_R = _ACL + "." + Acl._R;
    public static final String _PATH_ACL_RH = _ACL + "." + Acl._RH;


    //
    // Methods declarations -----------------------------------------------------------------------
    //
    @BsonProperty(_ACL)
    public Acl getAcl();

}
