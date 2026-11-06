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
 * BPM Process Step Subject (User or Group or Unit or External User) Interface
 *
 */
public interface ProcessSubject {

    /**
     * Returns systenId of User or Group.
     * Since v5.10.0 it may contain email in case external user is represented by this ProcessSubject
     *
     * @return
     */
    public String getId();


    /**
     * Sets systemId of User or Group.
     * Since v5.10.0 it may contain email in case external user is represented by this ProcessSubject
     *
     * @param systemId
     */
    public void setId(String systemId);


    /**
     * Returns name of the external user added ad hoc to the process
     *
     * @return
     * @since 5.10.0
     */
    public String getName();


    /**
     * Sets name of the external user added ad hoc to the process
     *
     * @param name
     * @since 5.10.0
     */
    public void setName(String name);

}
