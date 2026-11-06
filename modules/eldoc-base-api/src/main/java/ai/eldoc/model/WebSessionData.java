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
 * WebSession Data Model
 */
public class WebSessionData implements Serializable {

    // NOTE: This class placed in base-api lib in order to be able to have typed cache config in WebSessionManager like: MutableConfiguration<String, WebSessionData>,
    // as otherwise at least in the previous versions of Payara and while creating the cache on the remote node - it will not be able to load the Class WebSessionData and throw exceptions

    @Serial
    private static final long serialVersionUID = -4176345564646658751L;

    private final Serializable _id;
    private long _startTimestamp;
    private long _lastAccessed;
    private long _timeout;
    private String _host;
    private String _systemId;
    private String _tenantId;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public WebSessionData(Serializable id) {
        _id = id;
    }


    //
    // Getters & Setters (Virtual) ----------------------------------------------------------------
    //
    public Date getDateStart() {
        return new Date(getStartTimestamp());
    }


    public Date getDateLastAccessed() {
        return new Date(getLastAccessed());
    }


    public Date getDateExpires() {
        return new Date(getLastAccessed() + getTimeout());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public Serializable getId() {
        return _id;
    }


    public long getStartTimestamp() {
        return _startTimestamp;
    }


    public void setStartTimestamp(long startTimestamp) {
        _startTimestamp = startTimestamp;
    }


    public long getLastAccessed() {
        return _lastAccessed;
    }


    public void setLastAccessed(long lastAccessed) {
        _lastAccessed = lastAccessed;
    }


    public long getTimeout() {
        return _timeout;
    }


    public void setTimeout(long timeout) {
        _timeout = timeout;
    }


    public String getHost() {
        return _host;
    }


    public void setHost(String host) {
        _host = host;
    }


    public String getSystemId() {
        return _systemId;
    }


    public void setSystemId(String systemId) {
        _systemId = systemId;
    }


    public String getTenantId() {
        return _tenantId;
    }


    public void setTenantId(String tenantId) {
        _tenantId = tenantId;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        WebSessionData that = (WebSessionData) o;
        return _timeout == that._timeout && Objects.equals(_id, that._id) && Objects.equals(_startTimestamp, that._startTimestamp)
                && Objects.equals(_lastAccessed, that._lastAccessed) && Objects.equals(_host, that._host)
                && Objects.equals(_systemId, that._systemId) && Objects.equals(_tenantId, that._tenantId);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_id, _startTimestamp, _lastAccessed, _timeout, _host, _systemId, _tenantId);
    }
}
