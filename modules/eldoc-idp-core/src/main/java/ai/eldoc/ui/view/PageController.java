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

import ai.eldoc.TenantContext;
import ai.eldoc.WebSessionManager;
import ai.eldoc.constants.AppConstants;
import ai.eldoc.context.*;
import ai.eldoc.data.Operation;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.util.JsfUtils;
import jakarta.annotation.PostConstruct;
import jakarta.faces.annotation.RequestParameterMap;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.Strings;
import org.omnifaces.cdi.ViewScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import static ai.eldoc.constants.Pages.PAGE_ADMIN_CONFIG;
import static ai.eldoc.constants.Pages.PAGE_MAIN;
import static ai.eldoc.context.UserUiContext.UI_MODULE_IDP;
import static java.text.MessageFormat.format;


/**
 * Page Bean
 */
@Named
@ViewScoped
public class PageController implements Serializable {

    @Serial
    private static final long serialVersionUID = 586982525108210851L;

    private static final Logger LOG = LoggerFactory.getLogger(PageController.class);

    private static final String URL_PARAM_DEBUG = "debug";

    @Inject
    private ConfigContext _configContext;

    @Inject
    private WebSessionManager _webSessionManager;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private UserContext _userContext;

    @Inject
    private UIViewRoot _viewRoot;

    @Inject
    @RequestParameterMap
    private Map<String, String> _requestParamMap;

    @Inject
    private UserUiContext _userUiContext;

    private String _currentUrl;
    private boolean _modeEdit = false; // used on the views where model can be edited
    private String _pageTitlePrefix;
    private boolean _hideMainMenu;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    @PostConstruct
    void init() {
        if (JsfUtils.getRequest().getQueryString() != null && !JsfUtils.getRequest().getQueryString().isEmpty()) {
            _currentUrl = JsfUtils.getRequest().getRequestURL().append("?").append(JsfUtils.getRequest().getQueryString()).toString();
        } else {
            _currentUrl = JsfUtils.getRequest().getRequestURL().toString();
        }
        _webSessionManager.addOrUpdateWebSession(TenantContext.getCurrentTenantId(), SecurityUtils.getSubject());

        // Implement UI settings inherited from the user's session
        _hideMainMenu = _userUiContext.isHideMainMenu();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public String pageLoadGlobalHandler() {
        // Activate the required module based on the opened page:
        if (_viewRoot.getViewId().equals(PAGE_ADMIN_CONFIG)) {
            _hideMainMenu = true;
        }

        return null;
    }


    /**
     * Reloads current page. Method is deprecated and should not be used in the project
     */
    @Deprecated
    public void reloadPage() {
        try {
            JsfUtils.getExternalContext().redirect(_currentUrl);
        } catch (IOException ex) {
            LOG.error(null, ex);
        }
    }


    /**
     * Checks whether client's browser is IE (based on the User-Agent Request Header)
     *
     * @param request
     * @return
     */
    public boolean isIe(HttpServletRequest request) {
        return Strings.CS.contains(request.getHeader("user-agent"), "MSIE ") || Strings.CS.contains(request.getHeader("user-agent"), "Trident");
    }


    /**
     * UI Optimized method for hiding sensitive information / string values by hashing them
     *
     * @param input
     * @return
     */
    public String hashStringToBase62(String input) {
        return UserContext.hashStringToBase62(input);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    /**
     * Function returns page title (HTML title tag)
     *
     * @return
     */
    public String getPageTitle() {
        if (_pageTitlePrefix != null) {
            return _pageTitlePrefix.concat(" | ").concat(getPageTitleSuffix());
        } else {
            return getPageTitleSuffix();
        }
    }


    public String getPageDescription() {
        return "All-in-one Cloud Software for Intelligent Document Processing, Document Workflow Automation and Content Management";
    }


    public String getUserGuideUrl() {
        return "https://docs.eldoc.online/latest/user-guide";
    }


    public String getAdminGuideUrl() {
        return "https://docs.eldoc.online/latest/admin-guide";
    }


    public String getLogoLinkUrl() {
        return PAGE_MAIN;
    }


    public String getCssPageName() {
        return new StringBuilder().append("page").append(_viewRoot.getViewId().replace("/", "-").replace(".xhtml", "")).toString();
    }


    public String getTimeStamp() {
        String value = String.valueOf(System.currentTimeMillis());
        LOG.info(">> PageController.getTimeStamp(): {}", value);
        return value;
    }


    public String getFormattedMessage(String messageTemplate, Object... params) {
        return MessageFormat.format(messageTemplate, params);
    }


    public Set<String> getTimeZones() {
        // we use TreeSet for having TimeZones sorted in natural order
        return new TreeSet<>(ZoneId.getAvailableZoneIds());
    }


    public List<String> completeTimeZone(String query) {
        List<String> timeZoneList = new ArrayList<>(getTimeZones());
        return timeZoneList.stream()
                .filter(tz -> Strings.CI.contains(tz, query))
                .collect(Collectors.toList());
    }


    public List<String> completeTimeZoneWithDefault(String query) {
        List<String> timeZoneList = completeTimeZone(query);
        timeZoneList.addFirst(AppConstants.SELECT_ITEM_DEFAULT_VALUE);
        return timeZoneList;
    }


    public String getTimeZoneUiLabelByCode(String tzCode) {
        if (tzCode == null || tzCode.isBlank() || AppConstants.SELECT_ITEM_DEFAULT_VALUE.equalsIgnoreCase(tzCode)) {
            return _localeContext.getLabel("user.settings.timeZone.useDefault");
        }
        return tzCode;
    }


    public List<SelectItem> getDateFormatSI() {
        List<SelectItem> resultSI = new ArrayList<>();

        // Init time for sample
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 9);
        cal.set(Calendar.MONTH, Calendar.MARCH);

        // Define system-wide formats
        List<String> formats = List.of("dd.MM.yyyy", "dd/MM/yyyy", "dd.MM.yy", "dd/MM/yy",
                "dd.MMM.yyyy", "dd/MMM/yyyy", "dd.MMM.yy", "dd/MMM/yy",
                "MM.dd.yyyy", "MM/dd/yyyy", "MM.dd.yy", "MM/dd/yy",
                "MMM.dd.yyyy", "MMM/dd/yyyy", "MMM.dd.yy", "MMM/dd/yy",
                "yyyy/MM/dd", "yyyy.MM.dd", "yyyy-MM-dd");

        // Generate select items for UI
        formats.forEach(format -> {
            resultSI.add(new SelectItem(format, format("{0} ({1})", format, new SimpleDateFormat(format).format(cal.getTime()))));
        });

        return resultSI;
    }


    public List<SelectItem> getTimeFormatSI() {
        List<SelectItem> resultSI = new ArrayList<>();

        // Init time for sample
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 7);
        cal.set(Calendar.MINUTE, 9);

        // Define system-wide formats
        List<String> formats = List.of("H:mm", "HH:mm", "h:mm a", "hh:mm a");

        // Generate select items for UI
        formats.forEach(format -> {
            resultSI.add(new SelectItem(format, format("{0} ({1})", format, new SimpleDateFormat(format).format(cal.getTime()))));
        });
        return resultSI;
    }


    public List<SelectItem> getSystemLangSI() {
        List<SelectItem> result = new ArrayList<>();
        _configContext.getLangsEnabled().forEach(langCode -> result.add(new SelectItem(langCode, _localeContext.getLabel("general.lang." + langCode + ".label"))));
        return result;
    }


    /**
     * Checks whether the current session has licensed slot in case Concurrent licensing is enabled
     *
     * @return
     */
    public boolean hasLicensedSessionSlot() {
        return _webSessionManager.hasLicensedSessionSlot(_userContext.getId());
    }


    public boolean isModeDebug() {
        return Boolean.parseBoolean(_requestParamMap.get(URL_PARAM_DEBUG));
    }


    /**
     * Returns true in case document in editable mode
     *
     * @return
     */
    public boolean isModeEdit() {
        return _modeEdit;
    }


    public boolean isModeRead() {
        return !_modeEdit;
    }


    public void setModeEdit(boolean modeEdit) {
        _modeEdit = modeEdit;
    }


    public void setModeEdit() {
        setModeEdit(true);
    }


    public void setModeRead() {
        setModeEdit(false);
    }


    public String getPageTitlePrefix() {
        return _pageTitlePrefix;
    }


    public void setPageTitlePrefix(String pageTitlePrefix) {
        _pageTitlePrefix = pageTitlePrefix;
    }


    public String getPageTitleSuffix() {
        return "elDoc™ IDP OSS";
    }


    /**
     * Returns Operation Select Item List
     *
     * @return
     */
    public List<SelectItem> getOperationSI() {
        // TODO: consider moving method like this and similar for Comparison, Sort, etc to a dedicated UI Controller
        return List.of(
                new SelectItem(Operation.ADD, _localeContext.getLabel(Operation.getLabelId(Operation.ADD))),
                new SelectItem(Operation.SET, _localeContext.getLabel(Operation.getLabelId(Operation.SET))),
                new SelectItem(Operation.REMOVE, _localeContext.getLabel(Operation.getLabelId(Operation.REMOVE)))
        );
    }


    //
    // Page UI Customization related methods ------------------------------------------------------
    //
    public String getUiModule() {
        return UI_MODULE_IDP;
    }


    public boolean isModuleIdp() {
        return UI_MODULE_IDP.equals(getUiModule());
    }


    public String getUiModuleLabelId() {
        String defaultLabel = "general.module.select";
        if (getUiModule() == null) {
            return defaultLabel;
        }
        return switch (getUiModule()) {
            case UI_MODULE_IDP -> "general.module.idp";
            default -> defaultLabel;
        };
    }


    public void mainMenuShow() {
        _hideMainMenu = false;
    }


    public void mainMenuHide() {
        _hideMainMenu = true;
    }


    public boolean isHideMainMenu() {
        return _hideMainMenu;
    }


    public String getMenuLayoutClassAddon() {
        return isHideMainMenu() ? "layout-menu-static-inactive" : null;
    }


    public String getMenuButtonClassAddon() {
        return isHideMainMenu() ? "menu-button-rotate" : null;
    }
}
