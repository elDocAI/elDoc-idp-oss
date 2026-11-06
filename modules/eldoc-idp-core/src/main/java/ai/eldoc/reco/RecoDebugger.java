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
package ai.eldoc.reco;

import ai.eldoc.model.reco.*;
import java.io.Serializable;
import java.util.*;


/**
 * RecoDebugger Class for gathering matching process debugging info
 */
public class RecoDebugger implements Serializable {

    private static final long serialVersionUID = -451025451063336254L;

    public static final int ENTRY_TYPE_RECOSERVICE = 0;
    public static final int ENTRY_TYPE_FORM = 1;
    public static final int ENTRY_TYPE_PAGE = 2;
    public static final int ENTRY_TYPE_FIELD = 3;

    private boolean _enabled;
    private final List<RecoLogEntry> _logEntryList;
    private final List<RecoFormResult> _recoResultList;
    private final String _formName;
    private int _pageIdx;
    private RecoField _recoField;


    public static enum RECT_TYPE {
        BLIND_SRCH_RECT,
        BLIND_ANCHOR_RECT,
        ANCHOR_SRCH_RECT,
        ANCHOR_RECT,
        CELL,
        ANCHOR_UNDTBL_SRCH_RECT,
        ANCHOR_UNDTBL_RECT,
        VALUE_SRCH_RECT,
        VALUE_RECT
    }


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public RecoDebugger() {
        _logEntryList = Collections.synchronizedList(new ArrayList<>());
        _recoResultList = Collections.synchronizedList(new ArrayList<>());
        _formName = "recognitionService";
    }


    protected RecoDebugger(List<RecoLogEntry> logEntryList, List<RecoFormResult> recoResultList, String formName) {
        _logEntryList = logEntryList;
        _recoResultList = recoResultList;
        _formName = formName;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public RecoDebugger forForm(String formId) {
        return new RecoDebugger(_logEntryList, _recoResultList, formId).setDebugLogEnabled(isEnabled());
    }


    public void log(int entryType, String message) {
        log(entryType, message, null, null, null);
    }


    public void log(int entryType, String message, String value) {
        log(entryType, message, null, null, value);
    }


    public void log(int entryType, String message, Rectangle rectangle, RECT_TYPE rectType) {
        log(entryType, message, rectangle, rectType, null);
    }


    public void log(int entryType, String message, Rectangle rectangle, RECT_TYPE rectType, String value) {
        if (!isEnabled()) {
            return;
        }

        RecoLogEntry logEntry = new RecoLogEntry();

        logEntry.setRecoForm(_formName);
        logEntry.setPageIdx(_pageIdx);
        logEntry.setField(_recoField);

        logEntry.setMessage(message);
        logEntry.setRect(rectangle);
        logEntry.setRectType(rectType);
        logEntry.setEntryType(entryType);
        logEntry.setValue(value);
        _logEntryList.add(logEntry);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public void setPageIdx(int pageIdx) {
        _pageIdx = pageIdx;
    }


    public void setRecoField(RecoField recoField) {
        _recoField = recoField;
    }


    public List<RecoLogEntry> getLogEntryList() {
        return _logEntryList;
    }


    public List<RecoFormResult> getRecoResultList() {
        return _recoResultList;
    }


    public List<RecoFormResult> getRecoResultList(int fragmentStart, int fragmentEnd) {
        return _recoResultList.stream()
                .filter(result -> result.getFirstPageIdx() <= fragmentEnd && result.getLastPageIdx() >= fragmentStart)
                .toList();
    }


    public boolean isEnabled() {
        return _enabled;
    }


    public RecoDebugger setDebugLogEnabled(boolean debugLogEnabled) {
        _enabled = debugLogEnabled;
        return this;
    }


    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + (_enabled ? 1 : 0);
        hash = 67 * hash + Objects.hashCode(_logEntryList);
        hash = 67 * hash + Objects.hashCode(_recoResultList);
        hash = 67 * hash + Objects.hashCode(_formName);
        hash = 67 * hash + _pageIdx;
        hash = 67 * hash + Objects.hashCode(_recoField);
        return hash;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RecoDebugger other = (RecoDebugger) obj;
        if (_enabled != other._enabled) {
            return false;
        }
        if (_pageIdx != other._pageIdx) {
            return false;
        }
        if (!Objects.equals(_formName, other._formName)) {
            return false;
        }
        if (!Objects.equals(_recoResultList, other._recoResultList)) {
            return false;
        }
        if (!Objects.equals(_logEntryList, other._logEntryList)) {
            return false;
        }
        if (!Objects.equals(_recoField, other._recoField)) {
            return false;
        }
        return true;
    }


    //
    // Inner classes ------------------------------------------------------------------------------
    //
    public static class RecoLogEntry implements Serializable {

        private static final long serialVersionUID = 2469626991755480692L;

        private String _recoForm;
        private int _pageIdx;
        private RecoField _field;
        private int _entryType;

        private String _message;
        private Rectangle _rect;
        private RECT_TYPE _rectType;
        private String _value;


        public String getRecoForm() {
            return _recoForm;
        }


        public void setRecoForm(String recoForm) {
            _recoForm = recoForm;
        }


        public int getPageIdx() {
            return _pageIdx;
        }


        public void setPageIdx(int pageIdx) {
            _pageIdx = pageIdx;
        }


        public RecoField getField() {
            return _field;
        }


        public void setField(RecoField field) {
            _field = field;
        }


        public String getMessage() {
            return _message;
        }


        public void setMessage(String message) {
            _message = message;
        }


        public Rectangle getRect() {
            return _rect;
        }


        public void setRect(Rectangle rect) {
            _rect = rect;
        }


        public RECT_TYPE getRectType() {
            return _rectType;
        }


        public void setRectType(RECT_TYPE rectType) {
            _rectType = rectType;
        }


        public int getEntryType() {
            return _entryType;
        }


        public void setEntryType(int entryType) {
            _entryType = entryType;
        }


        public String getValue() {
            return _value;
        }


        public void setValue(String value) {
            _value = value;
        }


        @Override
        public int hashCode() {
            int hash = 3;
            hash = 73 * hash + Objects.hashCode(_recoForm);
            hash = 73 * hash + _pageIdx;
            hash = 73 * hash + Objects.hashCode(_field);
            hash = 73 * hash + Objects.hashCode(_message);
            hash = 73 * hash + Objects.hashCode(_rect);
            hash = 73 * hash + Objects.hashCode(_rectType);
            hash = 73 * hash + Objects.hashCode(_value);
            hash = 73 * hash + _entryType;
            return hash;
        }


        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RecoLogEntry other = (RecoLogEntry) obj;
            if (_recoForm == null ? other._recoForm != null : !_recoForm.equals(other._recoForm)) {
                return false;
            }
            if (_pageIdx != other._pageIdx) {
                return false;
            }
            if (!Objects.equals(_message, other._message)) {
                return false;
            }
            if (!Objects.equals(_field, other._field)) {
                return false;
            }
            if (!Objects.equals(_rect, other._rect)) {
                return false;
            }
            if (!Objects.equals(_entryType, other._entryType)) {
                return false;
            }
            if (_rectType != other._rectType) {
                return false;
            }
            if (!Objects.equals(_value, other._value)) {
                return false;
            }
            return true;
        }

    }

}
