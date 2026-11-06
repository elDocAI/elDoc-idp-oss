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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * System Utilities class helps to optimize interaction with various JVM system properties and Environment variables
 */
public final class SystemUtils {

    private static final Logger LOG = LoggerFactory.getLogger(SystemUtils.class);


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private SystemUtils() {
        // No public constructor
    }

    //
    // Methods ------------------------------------------------------------------------------------
    //

    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public static int getJvmOrEnvOrDefaultProperty(String jvmPropName, String envVarName, int defaultValue) {
        String logMsgPart = null;

        String rawIntStr = System.getProperty(jvmPropName);
        if (isNotBlank(rawIntStr)) {
            logMsgPart = "JVM param '" + jvmPropName + "'";
        } else {
            rawIntStr = System.getenv(envVarName);
            if (isNotBlank(rawIntStr)) {
                logMsgPart = "Env var '" + envVarName + "'";
            }
        }

        if (isNotBlank(rawIntStr)) {
            try {
                return Integer.parseInt(rawIntStr);
            } catch (NumberFormatException ex) {
                LOG.error("Error reading {} as int value: '{}', fallback to the default value: {}", logMsgPart, rawIntStr, defaultValue);
            }
        }

        return defaultValue;
    }


    public static boolean getJvmOrEnvOrDefaultProperty(String jvmPropName, String envVarName, boolean defaultValue) {
        // JVM system property (-Dkey=value)
        String propValue = System.getProperty(jvmPropName);
        if (isNotBlank(propValue)) {
            return Boolean.parseBoolean(propValue);
        }

        // Environment variable
        String envValue = System.getenv(envVarName);
        if (isNotBlank(envValue)) {
            return Boolean.parseBoolean(envValue);
        }

        // Default
        return defaultValue;
    }
}
