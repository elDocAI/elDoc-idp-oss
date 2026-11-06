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

import jakarta.servlet.http.HttpServletRequest;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import static ai.eldoc.api.RestApiUtils.HEADER_USER_AGENT;


/**
 * User Request Meta Model
 */
public class UserRequestMeta implements Serializable {

    @Serial
    private static final long serialVersionUID = 2926945140652397368L;

    private String _userAgent;
    private String _userIP;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private UserRequestMeta() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static UserRequestMeta fromRequest(HttpServletRequest request) {
        UserRequestMeta urm = new UserRequestMeta();
        urm.setUserIP(request.getRemoteAddr());
        urm.setUserAgent(request.getHeader(HEADER_USER_AGENT));
        return urm;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getUserAgent() {
        return _userAgent;
    }


    public void setUserAgent(String userAgent) {
        _userAgent = userAgent;
    }


    public String getUserIP() {
        return _userIP;
    }


    public void setUserIP(String userIP) {
        _userIP = userIP;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UserRequestMeta that = (UserRequestMeta) o;
        return Objects.equals(_userAgent, that._userAgent) && Objects.equals(_userIP, that._userIP);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_userAgent, _userIP);
    }
}
