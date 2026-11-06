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
package ai.eldoc.logging;

import ai.eldoc.TenantContext;
import java.io.*;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.*;
import java.util.logging.Formatter;


/**
 * elDoc ODL log formatter
 */
public class ODLLogFormatter extends Formatter {

    private static final boolean LOG_SOURCE_IN_KEY_VALUE = Boolean.getBoolean("ai.eldoc.logging.logSource");
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final String FIELD_BEGIN_MARKER = "[";
    private static final String FIELD_END_MARKER = "]";
    private static final String FIELD_SEPARATOR = " ";
    private static final String RFC_3339_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final String INDENT = "  ";

    private final LogManager _logManager = LogManager.getLogManager();

    /**
     * loggerResourceBundleTable caches references to all the ResourceBundle and can be searched using the LoggerName as the key
     */
    private final Map<String, ResourceBundle> _loggerResourceBundleTable = new HashMap<>();


    @Override
    public String format(LogRecord record) {
        return odlLogFormat(record);
    }


    @Override
    public String formatMessage(LogRecord record) {
        return odlLogFormat(record);
    }


    /**
     * Note: This method is not synchronized, we are assuming that the
     * synchronization will happen at the Log Handler.publish( ) method.
     */
    private String odlLogFormat(LogRecord record) {

        try {
            // creating message from log record using resource bundle and appending parameters
            String message = getLogMessage(record);
            if (message == null || message.isBlank()) {
                return "";
            }

            // Starting formatting message
            // Adding record begin marker
            StringBuilder recordBuffer = new StringBuilder();

            // Adding timestamp
            SimpleDateFormat dateFormatter = new SimpleDateFormat(RFC_3339_DATE_FORMAT);
            Date date = new Date();
            date.setTime(record.getMillis());
            String timestamp = dateFormatter.format(date);
            recordBuffer.append(FIELD_BEGIN_MARKER)
                    .append(timestamp)
                    .append(FIELD_END_MARKER).append(FIELD_SEPARATOR);

            // Adding messageType
            Level logLevel = record.getLevel();
            String odlLevel = logLevel.getLocalizedName();
            recordBuffer.append(FIELD_BEGIN_MARKER);
            recordBuffer.append(odlLevel);
            recordBuffer.append(FIELD_END_MARKER).append(FIELD_SEPARATOR);

            // Adding logger Name / module Name
            recordBuffer.append(FIELD_BEGIN_MARKER);
            String loggerName = record.getLoggerName();
            loggerName = (loggerName == null) ? "" : loggerName;
            recordBuffer.append(loggerName);
            recordBuffer.append(FIELD_END_MARKER).append(FIELD_SEPARATOR);

            // Adding thread ID
            // String threadName = Thread.currentThread().getName();
            recordBuffer.append(FIELD_BEGIN_MARKER)
                    .append("ThreadID=").append(record.getThreadID())
                    //.append(" ThreadName=").append(threadName)
                    .append(FIELD_END_MARKER).append(FIELD_SEPARATOR);

            // Adding extra Attributes - class name and method name for FINE and higher level messages
            Level level = record.getLevel();
            if (LOG_SOURCE_IN_KEY_VALUE || (level.intValue() <= Level.FINE.intValue())) {
                String sourceClassName = record.getSourceClassName();
                if (sourceClassName != null && !sourceClassName.isEmpty()) {
                    recordBuffer.append(FIELD_BEGIN_MARKER)
                            .append("CLASSNAME: ").append(sourceClassName)
                            .append(FIELD_END_MARKER).append(FIELD_SEPARATOR);
                }
                String sourceMethodName = record.getSourceMethodName();
                if (sourceMethodName != null && !sourceMethodName.isEmpty()) {
                    recordBuffer.append(FIELD_BEGIN_MARKER)
                            .append("METHODNAME: ").append(sourceMethodName)
                            .append(FIELD_END_MARKER).append(FIELD_SEPARATOR);
                }
            }

            // Adding Tenant name
            String tenantName = TenantContext.getCurrentTenantId() == null ? "" : TenantContext.getCurrentTenantId();
            recordBuffer.append(FIELD_BEGIN_MARKER)
                    .append(tenantName)
                    .append(FIELD_END_MARKER).append(FIELD_SEPARATOR);

            // Adding log message in multiline mode
            recordBuffer.append(FIELD_BEGIN_MARKER)
                    .append(FIELD_BEGIN_MARKER)
                    .append(LINE_SEPARATOR)
                    .append(INDENT);
            recordBuffer.append(message);
            recordBuffer.append(FIELD_END_MARKER).append(FIELD_END_MARKER);
            recordBuffer.append(LINE_SEPARATOR).append(LINE_SEPARATOR);

            return recordBuffer.toString();
        } catch (Exception ex) {
            new ErrorManager().error("Error in formatting Logrecord", ex, ErrorManager.FORMAT_FAILURE);
            // We've already notified the exception, the following return is to keep javac happy
            return "";
        }
    }


    private String getLogMessage(LogRecord record) throws IOException {
        String logMessage = record.getMessage();
        if (logMessage == null) {
            logMessage = "";
        }

        if (logMessage.contains("{0") && logMessage.contains("}") && record.getParameters() != null) {
            // If we find {0} or {1} etc., in the message, then it's most likely finer level messages for Method Entry, Exit etc.,
            logMessage = MessageFormat.format(logMessage, record.getParameters());
        } else {
            ResourceBundle rb = getResourceBundle(record.getLoggerName());
            if (rb != null && rb.containsKey(logMessage)) {
                try {
                    logMessage = MessageFormat.format(rb.getString(logMessage), record.getParameters());
                } catch (java.util.MissingResourceException e) {
                    // If we don't find an entry, then we are covered because the logMessage is initialized already
                }
            }
        }

        Throwable throwable = record.getThrown();
        if (throwable != null) {
            StringBuilder buffer = new StringBuilder();
            buffer.append(logMessage);
            buffer.append(LINE_SEPARATOR);
            try (StringWriter sw = new StringWriter()) {
                try (PrintWriter pw = new PrintWriter(sw)) {
                    throwable.printStackTrace(pw);
                }
                buffer.append(sw.toString());
                logMessage = buffer.toString();
            }
        }

        return logMessage;
    }


    private synchronized ResourceBundle getResourceBundle(String loggerName) {
        if (loggerName == null) {
            return null;
        }
        ResourceBundle rb = _loggerResourceBundleTable.get(loggerName);
        /*
         * Note that logManager.getLogger(loggerName) untrusted code may create loggers with
         * any arbitrary names this method should not be relied on so added code for checking null.
         */
        if (rb == null && _logManager.getLogger(loggerName) != null) {
            rb = _logManager.getLogger(loggerName).getResourceBundle();
            _loggerResourceBundleTable.put(loggerName, rb);
        }
        return rb;
    }
}
