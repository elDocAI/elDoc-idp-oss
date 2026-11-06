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

package ai.eldoc.ui.component;

import ai.eldoc.bpm.*;
import ai.eldoc.bpm.v3.ProcessStepImpl;
import ai.eldoc.service.LogHelper;
import ai.eldoc.util.JsfUtils;
import java.io.Serial;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;


/**
 * Class for working with process builder (UI)
 */
public class ProcBuilder implements Serializable {

    @Serial
    private static final long serialVersionUID = -4926164642334469874L;

    public static final String BEAN_NAME = "uiProcBuilder";
    public static final String DIALOG_ID = "procBldrDlg";

    private ProcessableDocument _procDS; // DataSource object that contains process
    private int _stateId; // ID of process, with which UIProcBuilder is currently working
    private boolean _editable;
    private ProcessState _procStateTemp; // ProcessState for internal temporary use


    // Constructor --------------------------------------------------------------------------------
    public ProcBuilder(ProcessableDocument procDS, int stateId, boolean editable) {
        try {
            _procDS = procDS;
            _stateId = stateId;
            _editable = editable;
            // Initializing local temp copy of processState
            _procStateTemp = _procDS.getProcess().createState(-1); // Creating proc state for internal temporary use
            _procStateTemp.getSteps().addAll(_procDS.getProcess().getState(_stateId).getSteps()); // Copy all steps from the original process

            _procStateTemp.removeEmptySteps();
        } catch (Exception e) {
            LogHelper.setEvent(e, "UIProcBuilder()");
        }
    }


    // Methods ------------------------------------------------------------------------------------
    public static void createInstance(ProcessableDocument procDS, int stateId, boolean editable) {
        JsfUtils.getViewMap().put(BEAN_NAME, new ProcBuilder(procDS, stateId, editable));
    }


    /**
     * Process State save
     */
    // public void btnOk(UIDialog dialog) {
    public void btnOk() {
        try {
            // Saving process:
            // Clean up process from empty States (states that does not have assigned participants)
            // As minimum one state is added by process route builder
            _procStateTemp.removeEmptySteps();
            // Clear participants of process set initially
            _procDS.getProcess().getState(_stateId).getSteps().clear();
            // Claculation of steps to transform Groups into Users, clear dublicates (but we do allow to keep initiator on manual input), etc.
            BpmEngine.getInstance().calculateState(null, _procDS.getProcess(), _procStateTemp, true, false, false);
            // Adding selected participants
            _procDS.getProcess().getState(_stateId).getSteps().addAll(_procStateTemp.getSteps());
        } catch (Exception e) {
            LogHelper.setEvent(e, "UIProcBuilder.btnOk()");
        }
    }


    // Getters & Setters --------------------------------------------------------------------------
    /**
     * Getting a process instance from a common process configuration
     *
     * @return ProcState - Process from common process configuration
     */
    public ProcessState getProcState() {
        return _procStateTemp;
    }


    public List<ProcessStep> getProcessSteps() {
        return getProcessStepsForUiBuilder(_procStateTemp, _editable);
    }


    public static List<ProcessStep> getProcessStepsForUiBuilder(ProcessState procState, boolean editable) {
        if (editable) {
            int size = procState.getSteps().size();
            if (size > 0) {
                // remove empty steps from the middle
                int i = 0;
                for (Iterator<ProcessStep> iter = procState.getSteps().listIterator(); iter.hasNext();) {
                    ProcessStep procStep = iter.next();
                    if (i > 0 && i < (size - 1) && procStep.getNamesAll().isEmpty()) {
                        iter.remove();
                    }
                    i++;
                }
            }

            size = procState.getSteps().size();
            // add empty step to the end
            if (!procState.getSteps().get(size - 1).getNamesAll().isEmpty()) {
                procState.getSteps().add(new ProcessStepImpl());
            }
        }
        return procState.getSteps();
    }
}
