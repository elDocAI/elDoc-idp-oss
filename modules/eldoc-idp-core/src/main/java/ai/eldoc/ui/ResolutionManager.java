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
package ai.eldoc.ui;

import ai.eldoc.model.WorkflowTask;
import java.util.List;


/**
 * Resolution Manager Bean Interface
 */
public interface ResolutionManager {

    public static enum RESOL_ACTION {
        RESOL_ADD,
        RESOL_EDIT,
        RESOL_DELETE
    }


    /**
     * Creates Resolution
     *
     * @param wfTask
     * @return
     */
    public boolean resolutionAdd(WorkflowTask wfTask);


    /**
     * Removes resolution
     *
     * @param wfTask
     * @return
     */
    public boolean resolutionRemove(WorkflowTask wfTask);


    /**
     * Executors management actions access rules
     *
     * @param respResolItem
     * @param action
     * @return
     */
    public boolean getResolutionAcl(RESOL_ACTION action, WorkflowTask respResolItem);


    /**
     * Returns list of resolutions assigned to the Process
     *
     * @return Resolution DocResponses list sorted by Step (ASC)
     */
    public List<WorkflowTask> getResolutionList();

}
