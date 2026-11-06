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

package ai.eldoc.context;

import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.model.AclContext;
import ai.eldoc.model.PreviewItem;
import ai.eldoc.model.directory.DirectoryItem;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.security.StringHasher;
import ai.eldoc.service.DirectoryService;
import io.seruco.encoding.base62.Base62;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.lang3.StringUtils;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


/**
 * User Session Bean
 */
@Named
@SessionScoped
public class UserContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1534374875287618828L;

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private ConfigContext _configContext;

    private DirectoryItem _userDS;
    private String _timezone;
    private String _dateFormat;
    private String _timeFormat;
    private int _userType = DirectoryConstants.USER_TYPE_ANONYNOUS;

    private final Map<String, PreviewItem> _previewItems = new PassiveExpiringMap<>(2, TimeUnit.HOURS, new ConcurrentHashMap<>()); // 2 hours TTL for entries
    private final Map<String, Object> _sessionSettings = new ConcurrentHashMap<>();


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Resets the bean to the initial state. Useful for cases when user re-logins with different logins, etc.
     */
    public void reset() {
        _timezone = null;
        _dateFormat = null;
        _timeFormat = null;
        _previewItems.clear();
        _sessionSettings.clear();
        _userType = DirectoryConstants.USER_TYPE_ANONYNOUS;
        refreshSubject();
    }


    /**
     * Refreshes inner cached Person document by reading it again from the DB
     */
    public void refreshSubject() {
        // This is required to handle system names like Anonymous or MaintAdmin
        _userDS = _directoryService.getDirectoryItem(getId());
        _userType = DirectoryConstants.USER_TYPE_INTERNAL;
    }


    //
    // Custom Getters & RunAs Methods -------------------------------------------------------------
    //


    /**
     * Returns user's systemId or systemId of another user on behalf of whom the current user currently acts.
     *
     * @return
     */
    public String getId() {
        return SecurityUtils.getSubjectSystemId();
    }


    /**
     * Returns the systemId of the user who actually executed the action, or null in case user acts using his own systemId
     *
     * @return systemId of the actual user or null in case user acts as himself
     */
    public String getDoneByActualName() {
        return SecurityUtils.getSubjectSystemIdOwnOrNullIfNotRunAs();
    }

    //
    // Static Methods -----------------------------------------------------------------------------
    //


    /**
     * UI Optimized method for hiding sensitive information / string values by hashing them
     *
     * @param input
     * @return
     */
    public static String hashStringToBase62(String input) {
        // TODO: How about implementing session keys for salting hash for the lifespan of the user's session
        return new String(Base62.createInstance().encode(StringHasher.hash(input, 256)));
    }


    //
    // Getters ------------------------------------------------------------------------------------
    //
    public DirectoryItem getUser() {
        if (_userDS == null) {
            refreshSubject();
        }
        return _userDS;
    }


    public String getNameShort() {
        if (getUser() != null) {
            return getUser().getItemNameUI();
        }
        return StringUtils.capitalize(DirectoryConstants.ANONYMOUS);
    }


    /**
     * Returns user preferred time-zone, or system time-zone in case user didn't set preferred one
     *
     * @return
     */
    public String getTimezone() {
        if (_timezone == null) {
            _timezone = _configContext.getSystemTimezone();
        }
        return _timezone;
    }


    /**
     * Returns user preferred date format, or system date format in case user didn't set preferred one
     *
     * @return
     */
    public String getDateFormat() {
        if (_dateFormat == null) {
            _dateFormat = _configContext.getAppConfig().getSystemDateFormat();
        }
        return _dateFormat;
    }


    /**
     * Returns user preferred time format, or system time format in case user didn't set preferred one
     *
     * @return
     */
    public String getTimeFormat() {
        if (_timeFormat == null) {
            _timeFormat = _configContext.getAppConfig().getSystemTimeFormat();
        }
        return _timeFormat;
    }


    /**
     * Returns hour format 12 or 24
     *
     * @return hour format 12 or 24
     */
    public String getHourFormat() {
        if (getTimeFormat() == null) {
            return "24";
        }
        return getTimeFormat().contains("a") ? "12" : "24"; // Based on the presence of "a" in the date-time formats: "H:mm", "HH:mm", "h:mm a", "hh:mm a"
    }


    /**
     * Returns user preferred date-time format, or system date-time format in case user didn't set preferred one
     *
     * @return
     */
    public String getDateTimeFormat() {
        return getDateFormat() + ' ' + getTimeFormat();
    }


    /**
     * Returns long time format what includes :ss (seconds)
     *
     * @return
     */
    public String getDateTimeLongFormat() {
        return getDateTimeFormat().replace(":mm", ":mm:ss");
    }


    /**
     * Returns short date format what replaces yyyy with yy
     *
     * @return
     */
    public String getDateTimeShortFormat() {
        return getDateTimeFormat().replace("yyyy", "yy");
    }


    /**
     * Returns User Type: 0 - Anonymous, 1 - Internal user, 2 - External User.<br>
     * Do not mix up with user directory type, which can be also internal and external, but that only for the 1 - Internal user
     *
     * @return user type code: 0 - Anonymous, 1 - Internal user, 2 - External User.
     */
    public int getUserType() {
        return _userType;
    }


    /**
     * Indicates if User Type: 1 - Internal user<br>
     * Do not mix up with user directory type, which can be also internal and external, but that only for the 1 - Internal user
     *
     * @return
     */
    public boolean isUserTypeInternal() {
        return getUserType() == DirectoryConstants.USER_TYPE_INTERNAL;
    }


    /**
     * Indicates if User Type: 2 - External User
     * Do not mix up with user directory type, which can be also internal and external, but that only for the 1 - Internal user
     *
     * @return
     */
    public boolean isUserTypeExternal() {
        return getUserType() == DirectoryConstants.USER_TYPE_EXTERNAL;
    }


    /**
     * Indicates if User Type: 0 - Anonymous
     * Do not mix up with user directory type, which can be also internal and external, but that only for the 1 - Internal user
     *
     * @return
     */
    public boolean isUserTypeAnonymous() {
        return getUserType() == DirectoryConstants.USER_TYPE_ANONYNOUS;
    }


    public boolean isAdminAny() {
        return SecurityUtils.isSubjectAdminAny();
    }


    public boolean isAdminDocRW() {
        return SecurityUtils.isSubjectAdminDocRW();
    }


    public boolean isAdminDocR() {
        return SecurityUtils.isSubjectAdminDocR();
    }


    public boolean isAdminDocD() {
        return SecurityUtils.isSubjectAdminDocD();
    }


    public boolean isAdminDocAny() {
        return SecurityUtils.isSubjectAdminDocAny();
    }


    public boolean isAdminSysR() {
        return SecurityUtils.isSubjectAdminSysR();
    }


    public boolean isAdminSysRW() {
        return SecurityUtils.isSubjectAdminSysRW();
    }


    public boolean isAdminSysAny() {
        return SecurityUtils.isSubjectAdminSysAny();
    }


    public boolean isMaintAdmin() {
        return DirectoryConstants.MAINTENANCE_USER_SYSTEMID.equals(getId());
    }


    public boolean isAnonymous() {
        return DirectoryConstants.ANONYMOUS.equals(getId());
    }


    public AclContext getAclContext() {
        return SecurityUtils.getSubjectAclContext();
    }


    public String putPreviewItem(PreviewItem previewItem) {
        String previewId = UUID.randomUUID().toString();
        _previewItems.put(previewId, previewItem);
        return previewId;
    }


    /**
     * Returns previewItem without removing it from the previewItems cache.
     * Must be used only with video files preview
     *
     * @param previewId
     * @return
     */
    public PreviewItem getPreviewItem(String previewId) {
        return _previewItems.get(previewId);
    }


    public PreviewItem getAndRemovePreviewItem(String previewId) {
        return _previewItems.remove(previewId);
    }


    public void removePreviewItemById(String previewItemId) {
        if (previewItemId == null) {
            return;
        }
        _previewItems.remove(previewItemId);
    }


    public Map<String, Object> getSessionSettings() {
        return _sessionSettings;
    }
}
