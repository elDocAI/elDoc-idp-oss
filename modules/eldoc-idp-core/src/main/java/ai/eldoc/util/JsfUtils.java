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
package ai.eldoc.util;

import jakarta.faces.application.Application;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.*;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.validator.ValidatorException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;


/**
 * JSF Utils Class
 */
public class JsfUtils {

    private static final Logger LOG = LoggerFactory.getLogger(JsfUtils.class);

    private static final String JSF_CUSTOM_TEMPLATES = "customMessages";
    private static final String JSF_STANDARD_TEMPLATES = "jakarta/faces/Messages";


    //
    // Label related methods ----------------------------------------------------------------------
    //
    public static Locale getLocale(FacesContext context) {
        Locale locale = null;
        // context.getViewRoot() may not have been initialized at this point.
        if (context != null && context.getViewRoot() != null) {
            locale = context.getViewRoot().getLocale();
        }
        // in case it is still null - init it here
        if (locale == null) {
            locale = I18n.LOCALE_ENG;
        }
        return locale;
    }


    public static String getLabel(FacesContext context, String labelKey) {
        return LabelUtils.getLabel(labelKey, getLocale(context));
    }


    /**
     * Returns JSF-embedded standard message template
     *
     * @param fc
     * @param templateId
     * @return
     */
    public static String getStandardMessageTemplate(FacesContext fc, String templateId) {
        ResourceBundle rb = ResourceBundle.getBundle(JSF_STANDARD_TEMPLATES, fc.getViewRoot().getLocale());
        return rb.getString(templateId);
    }


    //
    // Flash Messages related methods -------------------------------------------------------------
    //
    public static void addInfoMessage(String msg) {
        addFacesMessage(FacesMessage.SEVERITY_INFO, null, msg, null);
    }


    public static void addInfoMessage(String title, String details) {
        addFacesMessage(FacesMessage.SEVERITY_INFO, null, title, details);
    }


    public static void addInfoMessage(UIComponent component, String title) {
        addFacesMessage(FacesMessage.SEVERITY_INFO, component, title, null);
    }


    public static void addInfoMessage(UIComponent component, String title, String details) {
        addFacesMessage(FacesMessage.SEVERITY_INFO, component, title, details);
    }


    public static void addWarnMessage(String msg) {
        addFacesMessage(FacesMessage.SEVERITY_WARN, null, msg, null);
    }


    public static void addWarnMessage(String title, String details) {
        addFacesMessage(FacesMessage.SEVERITY_WARN, null, title, details);
    }


    public static void addErrorMessage(String msg) {
        addFacesMessage(FacesMessage.SEVERITY_ERROR, null, msg, null);
    }


    public static void addErrorMessage(String title, String details) {
        addFacesMessage(FacesMessage.SEVERITY_ERROR, null, title, details);
    }


    public static void addFacesMessage(FacesMessage.Severity severity, UIComponent component, String title, String details) {
        if (getFacesContext() == null) {
            return; // Nothing to do
        }
        String componentClientId = null;
        if (component != null) {
            componentClientId = component.getClientId(getFacesContext());
        }
        getFacesContext().addMessage(componentClientId, new FacesMessage(severity, title, details));
    }


    //
    // JSF Redirect and Navigation related methods ------------------------------------------------
    //
    public static void keepMessagesOnRedirect() {
        try {
            if (getFacesContext() != null && getFacesContext().getExternalContext() != null && getFacesContext().getExternalContext().getFlash() != null) {
                getFacesContext().getExternalContext().getFlash().setKeepMessages(true);
            }
        } catch (Exception e) {
        }
    }


    /**
     * Performs redirect to the given page ad-hoc (not JSF friendly method)
     * In most cases in JSF you should use the method {@link ai.eldoc.Util#navigate(String) Util.navigate(String)}
     *
     * @param pageName page name for redirect. Recommended to use static constants from {@link ai.eldoc.ui.view.PageController}
     */
    public static void redirect(final String pageName) {
        try {
            getExternalContext().redirect(pageName);
        } catch (IOException ex) {
            LOG.error(null, ex);
            throw new RuntimeException(ex);
        }
    }


    public static void redirect(String pageName, String paramName, String paramValue) {
        try {
            getExternalContext().redirect(pageName + "?" + paramName + "=" + paramValue);
        } catch (IOException ex) {
            LOG.error(null, ex);
            throw new RuntimeException(ex);
        }
    }


    /**
     * Redirects to the given page by using JSF's navigate handler (JSF friendly method)
     *
     * @param pageName page name for redirect. Recommended to use static constants from {@link ai.eldoc.ui.view.PageController}
     */
    public static void redirectToPage(final String pageName) {
        navigate(pageName);
    }


    public static String getNavigationTarget(String page) {
        return page + "?faces-redirect=true";
    }


    public static String getNavigationTarget(String page, String param, String paramValue) {
        return page + "?faces-redirect=true" + "&" + param + "=" + paramValue;
    }


    /**
     * Performs JSF navigation to target page with automatically added param "faces-redirect=true"
     *
     * @param targetPage
     */
    public static void navigate(final String targetPage) {
        if (targetPage.contains("?")) {
            getApplication().getNavigationHandler().handleNavigation(getFacesContext(), null, targetPage + "&faces-redirect=true");
        } else {
            getApplication().getNavigationHandler().handleNavigation(getFacesContext(), null, targetPage + "?faces-redirect=true");
        }
    }


    //
    // JSF Validation related methods -------------------------------------------------------------
    //
    /**
     * Throws ValidatorException
     *
     * @param message
     */
    public static void throwValidationException(String message) throws ValidatorException {
        throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR, message, null));
    }


    /**
     * Posts validation error message and invalidates provided component (if any)
     *
     * @param fc
     * @param component
     * @param message
     */
    public static void postValidationError(FacesContext fc, UIComponent component, String message) {
        postValidationError(fc, component, message, null);
    }


    /**
     * Posts validation error message and invalidates provided component (if any)
     *
     * @param fc
     * @param component
     * @param message
     * @param details
     */
    public static void postValidationError(FacesContext fc, UIComponent component, String message, String details) {
        FacesContext facesContext;
        if (fc == null) {
            facesContext = getFacesContext();
        } else {
            facesContext = fc;
        }
        FacesMessage msgObj = new FacesMessage(FacesMessage.SEVERITY_ERROR, message, details);
        if (component != null) {
            facesContext.addMessage(component.getClientId(facesContext), msgObj);
            if (component instanceof UIInput uIInput) {
                uIInput.setValid(false);
            }
        } else {
            facesContext.addMessage(null, msgObj);
        }
    }


    public static boolean isValidationFailed() {
        return getFacesContext().isValidationFailed();
    }


    /**
     * Adds validation event for component and message
     *
     * @param message
     */
    public static void postValidationError(String message) {
        postValidationError(null, null, message);
    }


    public static void postValidationRequiredError(FacesContext fc, UIComponent component, String label) {
        ResourceBundle rb = getCustomMessagesBundle(fc);
        String msg = MessageFormat.format(rb.getString("jakarta.faces.component.UIInput.REQUIRED"), label);
        postValidationError(fc, component, msg);
    }


    public static ResourceBundle getCustomMessagesBundle(FacesContext fc) {
        return ResourceBundle.getBundle(JSF_CUSTOM_TEMPLATES, fc.getViewRoot().getLocale());
    }


    /**
     * Returns protocol schema and server name only from the current request<br>
     * So in case the full app url looks like <code>http://demo.eldoc.com.ua:1001/elDoc</code><br>
     * this method will return <code>http://demo.eldoc.com.ua</code> only
     *
     * @return string representing URL or null in case of internal exception
     */
    public static String getServerUrlFromRequest() {
        try {
            String scheme = getRequest().getScheme();
            return new URL(scheme, getRequest().getServerName(), -1, "").toString();
        } catch (Exception ex) {
            LOG.error(null, ex);
        }
        return null;
    }


    //
    // JSF Generic Utility Methods ----------------------------------------------------------------
    //
    public static FacesContext getFacesContext() {
        return FacesContext.getCurrentInstance();
    }


    public static ExternalContext getExternalContext() {
        return getFacesContext().getExternalContext();
    }


    public static Application getApplication() {
        return getFacesContext().getApplication();
    }


    /**
     * Returns Map with Request Scope parameters
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getInitParamMap() {
        return getExternalContext().getInitParameterMap();
    }


    /**
     * Returns Map with pairs of GET parameters names and values
     *
     * @return
     */
    public static Map<String, String> getRequestParamMap() {
        return getExternalContext().getRequestParameterMap();
    }


    public static Map<String, String[]> getParamValuesMap() {
        return getExternalContext().getRequestParameterValuesMap();
    }


    public static Map<String, Object> getCookie() {
        return getExternalContext().getRequestCookieMap();
    }


    /**
     * Returns ApplicationMap<br>
     * The same as applicationScope
     *
     * @return
     */
    public static Map<String, Object> getApplicationMap() {
        return getExternalContext().getApplicationMap();
    }


    /**
     * Returns SessionMap<br>
     * The same as sessionScope
     *
     * @return
     */
    public static Map<String, Object> getSessionMap() {
        return getExternalContext().getSessionMap();
    }


    /**
     * Returns View Scope Map<br>
     *
     * @return
     */
    public static Map<String, Object> getViewMap() {
        return getFacesContext().getViewRoot().getViewMap();
    }


    /**
     * Returns RequestMap<br>
     * Represents requestScope
     *
     * @return
     */
    public static Map<String, Object> getRequestMap() {
        return getExternalContext().getRequestMap();
    }


    public static Map<String, String> getHeader() {
        return getExternalContext().getRequestHeaderMap();
    }


    public static Map<String, String[]> getHeaderValues() {
        return getExternalContext().getRequestHeaderValuesMap();
    }


    public static HttpServletRequest getRequest() {
        return (HttpServletRequest) getExternalContext().getRequest();
    }


    public static HttpServletResponse getResponse() {
        return (HttpServletResponse) getExternalContext().getResponse();
    }


    /**
     * Returns View Root
     *
     * @return
     */
    public static UIViewRoot getViewRoot() {
        return getFacesContext().getViewRoot();
    }
}
