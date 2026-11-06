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
package ai.eldoc.security;

import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


class LocalMaintenanceModeRealmTest {

    private static final String PASSWORD_PROPERTY = "ai.eldoc.maintenance.password";
    private static final String PASSWORD_ENVIRONMENT_VARIABLE = "ELDOC_AI_MAINTENANCE_PASSWORD";
    private static final String PASSWORD_PATTERN = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#&():?*~$^+=<>]).{10,50}$";

    private String originalPasswordProperty;


    @BeforeEach
    void resetRealmConfiguration() throws Exception {
        originalPasswordProperty = System.getProperty(PASSWORD_PROPERTY);
        System.clearProperty(PASSWORD_PROPERTY);
        defaultPasswordField().set(null, null);
    }


    @AfterEach
    void restoreRealmConfiguration() throws Exception {
        if (originalPasswordProperty == null) {
            System.clearProperty(PASSWORD_PROPERTY);
        } else {
            System.setProperty(PASSWORD_PROPERTY, originalPasswordProperty);
        }
        defaultPasswordField().set(null, null);
    }


    @Test
    void generatesAndPrintsAValidDefaultPasswordOnlyOnce() throws Exception {
        assumeTrue(System.getenv(PASSWORD_ENVIRONMENT_VARIABLE) == null);
        try (MaintenanceModeLogCapture capturedLogs = new MaintenanceModeLogCapture()) {
            new LocalMaintenanceModeRealm();
            String generatedPassword = String.valueOf((char[]) defaultPasswordField().get(null));

            assertTrue(Pattern.matches(PASSWORD_PATTERN, generatedPassword));
            List<LogRecord> warningRecords = capturedLogs.records().stream()
                    .filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
                    .toList();
            assertEquals(1, warningRecords.size());
            assertEquals(Level.WARNING, warningRecords.get(0).getLevel());
            assertTrue(warningRecords.get(0).getMessage().contains("[MAINTENANCE MODE] No password was configured."));
            assertTrue(warningRecords.get(0).getMessage().contains(generatedPassword));

            capturedLogs.clear();
            new LocalMaintenanceModeRealm();
            assertEquals(generatedPassword, String.valueOf((char[]) defaultPasswordField().get(null)));
            assertTrue(capturedLogs.records().isEmpty());
            assertTrue(isValid(new LocalMaintenanceModeRealm()));
        }
    }


    @Test
    void usesConfiguredJvmPasswordWithoutGeneratingOrPrintingDefault() throws Exception {
        String configuredPassword = "Configured9!Password";
        System.setProperty(PASSWORD_PROPERTY, configuredPassword);
        try (MaintenanceModeLogCapture capturedLogs = new MaintenanceModeLogCapture()) {
            new LocalMaintenanceModeRealm();

            assertNull(defaultPasswordField().get(null));
            assertEquals(configuredPassword, maintenancePassword(new LocalMaintenanceModeRealm()));
            assertTrue(capturedLogs.records().isEmpty());
        }
    }


    @Test
    void rejectsAnExplicitlyConfiguredWeakPasswordWithoutReplacingIt() throws Exception {
        System.setProperty(PASSWORD_PROPERTY, "weak");
        LocalMaintenanceModeRealm realm = new LocalMaintenanceModeRealm();

        assertNull(defaultPasswordField().get(null));
        assertEquals("weak", maintenancePassword(realm));
        assertFalse(isValid(realm));

        Method authenticationMethod = LocalMaintenanceModeRealm.class.getDeclaredMethod("doGetAuthenticationInfo", AuthenticationToken.class);
        authenticationMethod.setAccessible(true);
        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> authenticationMethod.invoke(realm, new UsernamePasswordToken("unknown", "weak")));
        assertInstanceOf(UnknownAccountException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().startsWith("Maintenance mode is disabled or not valid"));
    }


    @Test
    void validDefaultConfigurationPassesAuthenticationConfigurationCheck() throws Exception {
        assumeTrue(System.getenv(PASSWORD_ENVIRONMENT_VARIABLE) == null);
        new LocalMaintenanceModeRealm();

        Method authenticationMethod = LocalMaintenanceModeRealm.class.getDeclaredMethod("doGetAuthenticationInfo", AuthenticationToken.class);
        authenticationMethod.setAccessible(true);
        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> authenticationMethod.invoke(new LocalMaintenanceModeRealm(), new UsernamePasswordToken("unknown", "irrelevant")));

        assertInstanceOf(UnknownAccountException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().startsWith("Maintenance user record not found"));
    }


    private static Field defaultPasswordField() throws Exception {
        Field field = LocalMaintenanceModeRealm.class.getDeclaredField("ADMIN_PWD_DEFAULT");
        field.setAccessible(true);
        return field;
    }


    private static String maintenancePassword(LocalMaintenanceModeRealm realm) throws Exception {
        Method method = LocalMaintenanceModeRealm.class.getDeclaredMethod("getMaintenancePassword");
        method.setAccessible(true);
        return (String) method.invoke(realm);
    }


    private static boolean isValid(LocalMaintenanceModeRealm realm) throws Exception {
        Method method = LocalMaintenanceModeRealm.class.getDeclaredMethod("isValid");
        method.setAccessible(true);
        return (boolean) method.invoke(realm);
    }


    private static final class MaintenanceModeLogCapture extends Handler implements AutoCloseable {

        private final Logger logger = Logger.getLogger(LocalMaintenanceModeRealm.class.getName());
        private final Level originalLoggerLevel = logger.getLevel();
        private final boolean originalUseParentHandlers = logger.getUseParentHandlers();
        private final List<LogRecord> records = new ArrayList<>();


        private MaintenanceModeLogCapture() {
            setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
            logger.addHandler(this);
        }


        private List<LogRecord> records() {
            return records;
        }


        private void clear() {
            records.clear();
        }


        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                records.add(record);
            }
        }


        @Override
        public void flush() {
        }


        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setLevel(originalLoggerLevel);
            logger.setUseParentHandlers(originalUseParentHandlers);
        }
    }
}