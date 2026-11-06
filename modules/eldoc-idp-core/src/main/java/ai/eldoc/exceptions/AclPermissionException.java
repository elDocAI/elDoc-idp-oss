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
package ai.eldoc.exceptions;


import java.io.Serial;

/**
 * ACL Missing Permission Exception
 */
public class AclPermissionException extends AclException {

    @Serial
    private static final long serialVersionUID = 8336429949796360693L;

    //
    // Constructor --------------------------------------------------------------------------------
    //
    public AclPermissionException() {
        super("Missing Permission Exception");
    }


    public AclPermissionException(String message) {
        super(message);
    }


    public AclPermissionException(Throwable cause) {
        super(cause);
    }


    public AclPermissionException(String message, Throwable cause) {
        super(message, cause);
    }

}
