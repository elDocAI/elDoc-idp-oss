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

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * Process Monitoring Data Item
 */
public class ProcessMonitoringDataItem implements Serializable {

    @Serial
    private static final long serialVersionUID = -5499579117556992482L;

    private String _author;
    private int _state;
    private int _status;
    private int _step;
    private int _iteration;
    private int _taskStatus = -1; // Default value
    private String _taskStatusText = "general.TASK_STATUS_PENDING";
    private String _styleClass = "respData"; // base class
    private Date _logTimeIn;
    private Date _logTimeOut;
    private String _delegatedTo;
    private String _delegatedFrom;
    private boolean _isDelegated;
    private String _wfTaskId;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Creates delegated data item based on existing data item
     *
     * @return
     */
    public ProcessMonitoringDataItem createDelegatedDataItem() {
        ProcessMonitoringDataItem delegatedDataItem = new ProcessMonitoringDataItem();
        delegatedDataItem.setAuthor(_delegatedTo);
        delegatedDataItem.setDelegatedFrom(_author);
        delegatedDataItem.setState(_state);
        delegatedDataItem.setStatus(_status);
        delegatedDataItem.setStep(_step);
        delegatedDataItem.setIteration(_iteration);
        return delegatedDataItem;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getAuthor() {
        return _author;
    }


    public void setAuthor(String author) {
        _author = author;
    }


    public int getState() {
        return _state;
    }


    public void setState(int state) {
        _state = state;
    }


    public int getStatus() {
        return _status;
    }


    public void setStatus(int status) {
        _status = status;
    }


    public int getStep() {
        return _step;
    }


    public void setStep(int step) {
        _step = step;
    }


    public int getIteration() {
        return _iteration;
    }


    public void setIteration(int iteration) {
        _iteration = iteration;
    }


    public int getTaskStatus() {
        return _taskStatus;
    }


    public void setTaskStatus(int taskStatus) {
        _taskStatus = taskStatus;
    }


    public String getTaskStatusText() {
        return _taskStatusText;
    }


    public void setTaskStatusText(String taskStatusText) {
        _taskStatusText = taskStatusText;
    }


    public String getStyleClass() {
        return _styleClass;
    }


    public void setStyleClass(String styleClass) {
        _styleClass = styleClass;
    }


    public Date getLogTimeIn() {
        return _logTimeIn;
    }


    public void setLogTimeIn(Date logTimeIn) {
        _logTimeIn = logTimeIn;
    }


    public Date getLogTimeOut() {
        return _logTimeOut;
    }


    public void setLogTimeOut(Date logTimeOut) {
        _logTimeOut = logTimeOut;
    }


    public String getDelegatedTo() {
        return _delegatedTo;
    }


    public void setDelegatedTo(String delegatedTo) {
        _delegatedTo = delegatedTo;
    }


    public String getDelegatedFrom() {
        return _delegatedFrom;
    }


    public void setDelegatedFrom(String delegatedFrom) {
        _delegatedFrom = delegatedFrom;
    }


    public boolean isDelegated() {
        return _isDelegated;
    }


    public void setDelegated(boolean delegated) {
        _isDelegated = delegated;
    }


    public String getWfTaskId() {
        return _wfTaskId;
    }


    public void setWfTaskId(String wfTaskId) {
        _wfTaskId = wfTaskId;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProcessMonitoringDataItem that = (ProcessMonitoringDataItem) o;
        return _state == that._state && _status == that._status && _step == that._step && _iteration == that._iteration && _taskStatus == that._taskStatus && _isDelegated == that._isDelegated
                && Objects.equals(_author, that._author) && Objects.equals(_taskStatusText, that._taskStatusText) && Objects.equals(_styleClass, that._styleClass)
                && Objects.equals(_logTimeIn, that._logTimeIn) && Objects.equals(_logTimeOut, that._logTimeOut) && Objects.equals(_delegatedTo, that._delegatedTo)
                && Objects.equals(_delegatedFrom, that._delegatedFrom) && Objects.equals(_wfTaskId, that._wfTaskId);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_author, _state, _status, _step, _iteration, _taskStatus, _taskStatusText, _styleClass, _logTimeIn, _logTimeOut, _delegatedTo, _delegatedFrom, _isDelegated, _wfTaskId);
    }
}
