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
package ai.eldoc.ui.view;

import ai.eldoc.context.*;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.service.DirectoryService;
import ai.eldoc.util.JsfUtils;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.annotation.RequestParameterMap;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.web.util.SavedRequest;
import org.apache.shiro.web.util.WebUtils;
import org.omnifaces.util.Faces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import static ai.eldoc.constants.Pages.PAGE_LOGIN;
import static ai.eldoc.constants.Pages.PAGE_MAIN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Login Page Controller
 */
@Named
@RequestScoped
public class LoginController {

    private static final Logger LOG = LoggerFactory.getLogger(LoginController.class);

    public static final String VIEW_PARAM_ACTION = "action";
    public static final String VIEW_PARAM_TOKEN = "token";
    public static final String ACTION_PASSWORD_RECOVERY = "passwordRecovery";
    public static final String ACTION_PASSWORD_RESET = "passwordReset";

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private UserContext _userContext;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    @RequestParameterMap
    private Map<String, String> _requestMap;

    private String _username;
    private char[] _password = new char[]{};
    private boolean _rememberMe;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public String onLoginPageLoad() {
        if (SecurityUtils.getSubject().isAuthenticated() || SecurityUtils.getSubject().isRemembered()) {
            return JsfUtils.getNavigationTarget(PAGE_MAIN);
        }
        return null;
    }


    /**
     * Action perform login into the system
     *
     * @return
     */
    public String doLogin() {
        try {

            UsernamePasswordToken loginToken = new UsernamePasswordToken(_username, _password, _rememberMe, Faces.getRequestHostname());
            SecurityUtils.getSubject().login(loginToken);

            _configContext.setSystemUrlIfMissing(Faces.getRequestURL()); // On successful login, set the system URL in case it not set (helpful in a newly launched container)

            // Login succeeded
            _userContext.reset();

            SavedRequest savedRequest = WebUtils.getAndClearSavedRequest(Faces.getRequest());
            if (savedRequest != null && "GET".equals(savedRequest.getMethod())) {
                Faces.redirect(savedRequest.getRequestUrl());
                return null;
            } else {
                return JsfUtils.getNavigationTarget(PAGE_MAIN);
            }
        } catch (AuthenticationException ae) {
            // Do nothing
            LOG.debug("Login error: {}", ae.toString());
        } catch (Exception ex) {
            LOG.debug("Login error: {}", ex.toString(), ex);
        }
        JsfUtils.addErrorMessage(_localeContext.getLabel("msg.login.error"));

        return null;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getUsername() {
        return _username;
    }


    public void setUsername(String username) {
        _username = username;
    }


    public String getPassword() {
        return new String(_password);
    }


    public void setPassword(String password) {
        _password = password.toCharArray();
    }


    public boolean getRememberMe() {
        return _rememberMe;
    }


    public void setRememberMe(boolean rememberMe) {
        _rememberMe = rememberMe;
    }


    public String getViewActionParam() {
        return _requestMap.get(VIEW_PARAM_ACTION);
    }


    public String getViewTokenParam() {
        return _requestMap.get(VIEW_PARAM_TOKEN);
    }


    public boolean isLoginFormRendered() {
        return !isPasswordRecoveryFormRendered() && !isPasswordResetFormRendered();
    }


    public boolean isPasswordRecoveryFormRendered() {
        return getViewActionParam() != null && ACTION_PASSWORD_RECOVERY.equals(getViewActionParam());
    }


    public boolean isPasswordResetFormRendered() {
        return getViewActionParam() != null && ACTION_PASSWORD_RESET.equals(getViewActionParam()) && isNotBlank(getViewTokenParam());
    }


    public String getLoginPageHref() {
        return PAGE_LOGIN;
    }


    public String getPasswordRecoveryHref() {
        return "?".concat(VIEW_PARAM_ACTION).concat("=").concat(ACTION_PASSWORD_RECOVERY);
    }
}
