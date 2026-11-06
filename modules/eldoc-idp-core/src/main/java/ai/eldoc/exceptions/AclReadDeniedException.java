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
 * ACL Read Forbidden Exception
 */
public class AclReadDeniedException extends AclPermissionException {

    @Serial
    private static final long serialVersionUID = -1471558726474892516L;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public AclReadDeniedException() {
        super("Read Permission Missing Exception");
    }


    /**
     * Constructor for creating exception with predefined message format: EntityName[entityId] read permission is missing for systemId[systemId]
     *
     * @param entityName
     * @param entityId
     * @param systemId
     */
    public AclReadDeniedException(String entityName, String entityId, String systemId) {
        super(String.format("%s[%s] read permission is missing for systemId[%s]", entityName, entityId, systemId));
    }


    public AclReadDeniedException(String message) {
        super(message);
    }


    public AclReadDeniedException(Throwable cause) {
        super(cause);
    }


    public AclReadDeniedException(String message, Throwable cause) {
        super(message, cause);
    }

}
