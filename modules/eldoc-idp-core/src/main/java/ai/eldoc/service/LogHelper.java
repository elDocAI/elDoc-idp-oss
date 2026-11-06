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
package ai.eldoc.service;

import ai.eldoc.TenantContext;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.dao.DaoUtils;
import ai.eldoc.model.Log;
import ai.eldoc.model.LogExt;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.util.I18n;
import ai.eldoc.util.JsfUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.ResourceBundle;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Log Helper
 */
public class LogHelper {

    private static final Logger LOG = LoggerFactory.getLogger(LogHelper.class);

    private static final String LOG_LANG = "en"; // language for logging strings in elDocLogDB

    public final static int ERROR = 1;
    public final static int WARNING = 2;
    public final static int INFO = 3;
    public final static int SUCCESS = 4;

    private static final String ERROR_STR = "error";
    private static final String WARNING_STR = "warning";
    private static final String INFO_STR = "info";
    private static final String SUCCESS_STR = "success";

    private static final Map<String, Integer> SEVERITY_NAME_TO_ID_MAP = Map.of(
            ERROR_STR, ERROR,
            WARNING_STR, WARNING,
            INFO_STR, INFO,
            SUCCESS_STR, SUCCESS
    );

    private static final String EVENTS_BUNDLE_NAME = "events";
    private static final ResourceBundle EVENTS_BUNDLE = LogHelper.getEventsBundle();


    //
    // Static Methods -----------------------------------------------------------------------------
    //
    /**
     * Shows message on the UI retrieved by eventId
     * You need to call {@link ai.eldoc.Util.keepMessagesOnRedirect()} in case redirect is performed after using this method
     *
     * @param eventId
     * @param localeLang
     */
    public static void setFlashByEventId(String eventId, String localeLang) {
        if (isBlank(eventId) || !EVENTS_BUNDLE.containsKey(eventId.concat(".severity"))) {
            LOG.warn("No eventId [{}.severity] key found", eventId);
            return;
        }

        String severity = EVENTS_BUNDLE.getString(eventId.concat(".severity"));
        if (!SEVERITY_NAME_TO_ID_MAP.containsKey(severity)) {
            LOG.warn("Severity '{}' for eventId '{}' is not valid", severity, eventId);
            return;
        }

        String message = getMessageForEventId(eventId, localeLang, false);
        if (isBlank(message)) {
            LOG.warn("Message for eventId [{}] and locale [{}] is blank", eventId, localeLang);
            return;
        }

        setFlash(severity, message);
    }


    /**
     * Logging method for logging event in the DB.
     * It will call Util.keepMessagesOnRedirect(); by itself in case message should be shown on the JSF UI
     *
     * @param eventId of the message
     */
    public static void setEvent(String eventId) {
        setEventInner(eventId, null, null, null, null);
    }


    /**
     * Logging method for logging event in the DB. It will call Util.keepMessagesOnRedirect(); by itself in case message should be shown on the JSF UI
     *
     * @param e
     * @param description
     */
    public static void setEvent(Exception e, String description) {
        setEventInner(null, e, description, null, null);
    }


    /**
     * Logging method for logging event in the DB.It will call Util.keepMessagesOnRedirect(); by itself in case message should be shown on the JSF UI
     *
     * @param eventId of the message
     * @param e
     */
    public static void setEvent(String eventId, Exception e) {
        setEventInner(eventId, e, null, null, null);
    }


    /**
     * Logging method for logging event in the DB.It will call Util.keepMessagesOnRedirect(); by itself in case message should be shown on the JSF UI
     *
     * @param eventId of the message
     * @param e
     * @param description
     */
    public static void setEvent(String eventId, Exception e, String description) {
        setEventInner(eventId, e, description, null, null);
    }


    /**
     * Logging method for logging event in the DB.It will call Util.keepMessagesOnRedirect(); by itself in case message should be shown on the JSF UI
     *
     * @param eventId of the message
     * @param description
     */
    public static void setEvent(String eventId, String description) {
        setEventInner(eventId, null, description, null, null);
    }


    /**
     * Logging method for logging event in the DB.It will call Util.keepMessagesOnRedirect(); by itself in case message should be shown on the JSF UI
     *
     * @param eventId of the message
     * @param e
     * @param description
     * @param logExt
     */
    public static void setEvent(String eventId, Throwable e, String description, LogExt logExt) {
        setEventInner(eventId, e, description, logExt, null);
    }


    public static void setEvent(String eventId, Throwable e, String description, LogExt logExt, Object[] descriptionTemplateValues) {
        setEventInner(eventId, e, description, logExt, descriptionTemplateValues);
    }


    public static void saveOrIncrementDailyCount(String eventId, Throwable e, String description, LogExt logExt) {
        saveOrIncrementDailyCountInner(eventId, e, description, logExt, null);
    }


    public static void saveOrIncrementDailyCount(String eventId, Throwable e, String description, LogExt logExt, Object[] descriptionTemplateValues) {
        saveOrIncrementDailyCountInner(eventId, e, description, logExt, descriptionTemplateValues);
    }


    //
    // Static Methods (private) -------------------------------------------------------------------
    //
    private static void setEventInner(String eventId, Throwable eParam, String description, LogExt logExt, Object[] descriptionTemplateValues) {
        try {
            if (TenantContext.getCurrentTenantId() == null) {
                // We do not proceed in case tenant context is not available
                LOG.error("No TenantContext is available, cannot log eventId=" + eventId + "; message=" + description + "; logExt=" + logExt, eParam);
                return;
            }

            Log log = createLog(eventId, eParam, description, logExt, descriptionTemplateValues);

            DaoUtils.getLogDao().create(null, log);

            if (logExt != null && isNotBlank(logExt.getDuplicateTenantId()) && AppConfigService.getInstance().getTenantIdSet().contains(logExt.getDuplicateTenantId())) {
                // LogExt requested duplicating logs to other tenant and tenant is present in system
                String initialTenantId = TenantContext.getCurrentTenantId();
                try {
                    TenantContext.setCurrentTenant(logExt.getDuplicateTenantId());
                    DaoUtils.getLogDao().create(null, log);
                } finally {
                    TenantContext.setCurrentTenant(initialTenantId);
                }
            }
        } catch (Exception e) {
            LOG.error("CRITICAL : No possibility to log event: " + eventId, e);
        }
    }


    public static void saveOrIncrementDailyCountInner(String eventId, Throwable eParam, String description, LogExt logExt, Object[] descriptionTemplateValues) {
        try {
            if (TenantContext.getCurrentTenantId() == null) {
                // We do not proceed in case tenant context is not available
                LOG.error("No TenantContext is available, cannot log eventId=" + eventId + "; message=" + description + "; logExt=" + logExt, eParam);
                return;
            }

            Log log = createLog(eventId, eParam, description, logExt, descriptionTemplateValues);

            DaoUtils.getLogDao().createOrIncrementDailyCount(null, log);

            if (logExt != null && isNotBlank(logExt.getDuplicateTenantId()) && AppConfigService.getInstance().getTenantIdSet().contains(logExt.getDuplicateTenantId())) {
                // LogExt requested duplicating logs to other tenant and tenant is present in system
                String initialTenantId = TenantContext.getCurrentTenantId();
                try {
                    TenantContext.setCurrentTenant(logExt.getDuplicateTenantId());
                    DaoUtils.getLogDao().createOrIncrementDailyCount(null, log);
                } finally {
                    TenantContext.setCurrentTenant(initialTenantId);
                }
            }
        } catch (Exception e) {
            LOG.error("CRITICAL : No possibility to log event: " + eventId, e);
        }

    }


    private static Log createLog(String eventId, Throwable eParam, String description, LogExt logExt, Object[] descriptionTemplateValues) {
        String severity = WARNING_STR; // default message level - warning
        String messageForUi = null;
        String messageForDbLog = null;
        String msgLogExt = null;
        String msgLogFinal = null;

        if (eParam != null) {
            // in case exception is set - change default level to error
            severity = ERROR_STR;
        }

        // Get severity of the event. If no severity - set warning by default
        if (isNotBlank(eventId) && EVENTS_BUNDLE.containsKey(eventId + ".severity")) {
            severity = EVENTS_BUNDLE.getString(eventId + ".severity");
            if (!SEVERITY_NAME_TO_ID_MAP.containsKey(severity)) {
                // reset to warning
                severity = WARNING_STR;
                LOG.warn("Severity '{}' for eventId '{}' is not valid, reset to '{}'", severity, eventId, WARNING_STR);
            }
        }

        // Get i18n action message for the event (for logging into elDocLogDB).
        if (isNotBlank(eventId)) {
            messageForDbLog = getMessageForEventId(eventId, LOG_LANG, false);
            if (messageForDbLog == null) {
                messageForDbLog = "No action message defined for eventId: " + eventId;
            }
        }

        // If has .show key - show event to User via setFlash
        if (isNotBlank(eventId) && EVENTS_BUNDLE.containsKey(eventId + ".show")) {
            if (JsfUtils.getFacesContext() != null) {
                // Get i18n action message for the event (for flashMsg)
                messageForUi = getMessageForEventId(eventId, I18n.getLangSysCodeByLocale(JsfUtils.getLocale(JsfUtils.getFacesContext())), false);
                if (messageForUi == null) {
                    messageForUi = "No action message for the event: " + eventId;
                }
                // Decide show or not eventID in the flash message
                if (ERROR_STR.equals(severity) || WARNING_STR.equals(severity)) {
                    setFlash(severity, (eventId + ": " + messageForUi));
                } else {
                    setFlash(severity, messageForUi);
                }
                JsfUtils.keepMessagesOnRedirect();
            }
        }

        // pars ext log data if exists
        if (isNotBlank(description)) {
            msgLogExt = description;
        }

        String systemId;
        String systemIdBy = null;
        try {
            systemId = SecurityUtils.getSubjectSystemId();
            systemIdBy = SecurityUtils.getSubjectSystemIdOwnOrNullIfNotRunAs();
        } catch (Exception e) {
            LOG.trace("Subject's systemId cannot be retrieved, fallback to: {}", DirectoryConstants.ELDOC_ROBOT, e);
            systemId = DirectoryConstants.ELDOC_ROBOT;
        }

        // Save event in the Log DB
        Log log = new Log(systemId, systemIdBy);
        log.setEventId(eventId);
        log.setSeverity(SEVERITY_NAME_TO_ID_MAP.get(severity));

        if (eParam != null) {
            StringWriter errors = new StringWriter();
            eParam.printStackTrace(new PrintWriter(errors));
            log.setStackTrace(errors.toString());
        }

        if (isNotBlank(messageForDbLog) && isNotBlank(msgLogExt)) {
            msgLogFinal = messageForDbLog + " | " + msgLogExt;
        } else if (isNotBlank(messageForDbLog) && descriptionTemplateValues != null) {
            msgLogFinal = format(messageForDbLog, descriptionTemplateValues);
        } else if (isBlank(messageForDbLog) && isNotBlank(msgLogExt)) {
            msgLogFinal = msgLogExt;
        } else if (isNotBlank(messageForDbLog) && isBlank(msgLogExt)) {
            msgLogFinal = messageForDbLog;
        }

        log.setEvent(msgLogFinal);

        if (logExt != null) {
            log.initWithLogExt(logExt);
        }

        return log;
    }


    private static ResourceBundle getEventsBundle() {
        try {
            return ResourceBundle.getBundle(EVENTS_BUNDLE_NAME);
        } catch (Exception e) {
            LOG.error("Can not locate events bundle: " + EVENTS_BUNDLE_NAME, e);
        }
        return null;
    }


    private static String getMessageForEventId(String eventId, String lang, boolean includeEventId) {
        String postFix;
        try {
            if (lang.equals("uk")) {
                postFix = ".msg.uk";
            } else {
                postFix = ".msg.en";
            }
            if (EVENTS_BUNDLE.containsKey(eventId + postFix)) {
                if (includeEventId) {
                    return eventId + ": " + EVENTS_BUNDLE.getString(eventId + postFix);
                } else {
                    return EVENTS_BUNDLE.getString(eventId + postFix);
                }
            }
        } catch (Exception e) {
            LOG.error("Error getting message for eventId=" + eventId + "; lang=" + lang, e);
        }
        return null;
    }


    /**
     * Adds Faces Message
     *
     * @param level
     * @param message
     */
    private static void setFlash(final String level, final String message) {
        switch (level) {
            case ERROR_STR:
                JsfUtils.addErrorMessage(message);
                return;
            case WARNING_STR:
                JsfUtils.addWarnMessage(message);
                return;
            default:
                JsfUtils.addInfoMessage(message);
        }
    }
}
