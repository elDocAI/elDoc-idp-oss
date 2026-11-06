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

import jakarta.ejb.ApplicationException;
import java.io.Serial;


/**
 * BPM Exception indicates that group can not be found by given person and tag
 */
@ApplicationException
public class BpmNameGroupByMemberAndTagNotFoundException extends BpmNameNotResolvedException {

    @Serial
    private static final long serialVersionUID = 1765230859288757202L;

    private String _tag;


    public BpmNameGroupByMemberAndTagNotFoundException() {
        super("Group with the given tag and systemId can not be found");
    }


    public BpmNameGroupByMemberAndTagNotFoundException(String message) {
        super(message);
    }


    public BpmNameGroupByMemberAndTagNotFoundException(Throwable cause) {
        super(cause);
    }


    public BpmNameGroupByMemberAndTagNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }


    public String getTag() {
        return _tag;
    }


    public BpmNameGroupByMemberAndTagNotFoundException setTag(String tag) {
        _tag = tag;
        return this;
    }

}
