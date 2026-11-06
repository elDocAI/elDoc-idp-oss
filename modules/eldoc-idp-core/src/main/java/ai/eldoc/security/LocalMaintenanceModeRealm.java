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

import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.service.AppConfigService;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;


/**
 * Security realm used in maintenance mode
 */
public final class LocalMaintenanceModeRealm extends AuthorizingRealm implements RolesAwareRealm {

    private static final Logger LOG = LoggerFactory.getLogger(LocalMaintenanceModeRealm.class);
    private static final String REALM_NAME = "localMaintenanceModeRealm";

    private static final String ELDOC_AI_MAINTENANCE_LOGIN_JVM_OPTION = "ai.eldoc.maintenance.login";
    private static final String ELDOC_AI_MAINTENANCE_LOGIN_SYSTEM_VAR = "ELDOC_AI_MAINTENANCE_LOGIN";
    private static final String ADMIN_LOGIN_DEFAULT = "admin";

    private static final String ELDOC_AI_MAINTENANCE_PASSWORD_JVM_OPTION = "ai.eldoc.maintenance.password";
    private static final String ELDOC_AI_MAINTENANCE_PASSWORD_SYSTEM_VAR = "ELDOC_AI_MAINTENANCE_PASSWORD";

    private static final String PASSWORD_PATTERN_STR = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#&():?*~$^+=<>]).{10,50}$";
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(PASSWORD_PATTERN_STR);
    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();

    private static volatile char[] ADMIN_PWD_DEFAULT = null;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public LocalMaintenanceModeRealm() {
        super();
        setAuthenticationTokenClass(UsernamePasswordToken.class);
        initDefaultAccessIfRequired();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        UsernamePasswordToken upToken = (UsernamePasswordToken) token;
        // Try elDoc maintenance mode settings
        if (!isValid()) {
            throw new UnknownAccountException("Maintenance mode is disabled or not valid, login: " + upToken.getUsername());
        }

        // Compare Logins first in order to see whether we need to proceed with this Real at all
        if (Objects.equals(getMaintenanceLogin(), upToken.getUsername())) {
            List<String> tenantIdList = new ArrayList<>(AppConfigService.getInstance().getTenantIdSet());

            // Proceed with auth
            if (Objects.equals(getMaintenancePassword(), String.valueOf(upToken.getPassword()))) {
                return new SimpleAuthenticationInfo(
                        new Identity(DirectoryConstants.MAINTENANCE_USER_SYSTEMID, tenantIdList),
                        upToken.getPassword(),
                        getName()
                );
            }
        }

        throw new UnknownAccountException("Maintenance user record not found by submitted login: " + upToken.getUsername());
    }


    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(final PrincipalCollection pc) {
        LOG.debug("Get Authorization Info [{}] for Identity: '{}'", getName(), pc.getPrimaryPrincipal());
        SimpleAuthorizationInfo authorizationInfo = new SimpleAuthorizationInfo();
        if (DirectoryConstants.MAINTENANCE_USER_SYSTEMID.equals(((Identity) pc.getPrimaryPrincipal()).getSystemId())) {
            // we check here only local users
            LOG.debug("Get Authorization Info [{}]: systemId '{}' accepted", getName(), pc.getPrimaryPrincipal());
            authorizationInfo.addRole(DirectoryConstants.ROLE_MAINT_ADMIN);
            authorizationInfo.addRole(DirectoryConstants.ROLE_ALLUSERS); // we mark all users from local user DS as users that have access to elDoc
            authorizationInfo.addRoles(DirectoryConstants.ROLE_ADMINSYS_ALL);
            authorizationInfo.addRoles(DirectoryConstants.ROLE_ADMINDOC_ALL);
            authorizationInfo.addStringPermission("admin:view");
        } else {
            LOG.debug("Get Authorization Info [{}]: not applicable for systemId '{}'", getName(), pc.getPrimaryPrincipal());
        }
        return authorizationInfo;
    }


    private boolean isValid() {
        if (getMaintenanceLogin() == null || getMaintenanceLogin().trim().length() < 5) {
            LOG.warn("[MAINTENANCE MODE] Validation error: login is not set or is shorter than 5 characters");
            return false;
        }

        if (getMaintenancePassword() == null || !PASSWORD_PATTERN.matcher(getMaintenancePassword()).matches()) {
            LOG.warn("[MAINTENANCE MODE] Validation error: password is not set or doesn't meet complexity requirements");
            return false;
        }

        return true;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getRoles(final PrincipalCollection pc) {
        AuthorizationInfo authzInfo = getAuthorizationInfo(pc);
        if (authzInfo instanceof SimpleAuthorizationInfo simpleAuthorizationInfo && simpleAuthorizationInfo.getRoles() != null) {
            return simpleAuthorizationInfo.getRoles();
        }
        return new HashSet<>();
    }


    @Override
    public String getName() {
        return REALM_NAME;
    }


    //
    // Private Methods ----------------------------------------------------------------------------
    //
    private static String getMaintenanceLogin() {
        return Objects.requireNonNullElse(System.getProperty(ELDOC_AI_MAINTENANCE_LOGIN_JVM_OPTION, System.getenv(ELDOC_AI_MAINTENANCE_LOGIN_SYSTEM_VAR)), ADMIN_LOGIN_DEFAULT);
    }


    private static String getConfiguredMaintenancePassword() {
        return System.getProperty(ELDOC_AI_MAINTENANCE_PASSWORD_JVM_OPTION, System.getenv(ELDOC_AI_MAINTENANCE_PASSWORD_SYSTEM_VAR));
    }


    private static synchronized void initDefaultAccessIfRequired() {
        // Keep the configured password or reuse the previously generated default.
        if (getConfiguredMaintenancePassword() != null || ADMIN_PWD_DEFAULT != null) {
            return;
        }

        final String PASSWORD_UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final String PASSWORD_LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
        final String PASSWORD_NUMBERS = "0123456789";
        final String PASSWORD_SPECIAL_CHARACTERS = "!@#&:?*~$^+=";
        String allCharacters = PASSWORD_UPPERCASE + PASSWORD_LOWERCASE + PASSWORD_NUMBERS;
        // Generate 10 characters with uppercase, lowercase, digits and exactly one special character.
        char[] password = new char[10];
        password[0] = randomCharacter(PASSWORD_UPPERCASE);
        password[1] = randomCharacter(PASSWORD_LOWERCASE);
        password[2] = randomCharacter(PASSWORD_NUMBERS);
        password[3] = randomCharacter(PASSWORD_SPECIAL_CHARACTERS);
        for (int index = 4; index < password.length; index++) {
            password[index] = randomCharacter(allCharacters);
        }
        // Shuffle to randomize the positions of the required character types.
        for (int index = password.length - 1; index > 0; index--) {
            int swapIndex = PASSWORD_RANDOM.nextInt(index + 1);
            char character = password[index];
            password[index] = password[swapIndex];
            password[swapIndex] = character;
        }

        // Keep the special character away from the first and last positions.
        if (PASSWORD_SPECIAL_CHARACTERS.indexOf(password[0]) >= 0 || PASSWORD_SPECIAL_CHARACTERS.indexOf(password[password.length - 1]) >= 0) {
            int index = PASSWORD_SPECIAL_CHARACTERS.indexOf(password[0]) >= 0 ? 0 : password.length - 1;
            int swapIndex = 1 + PASSWORD_RANDOM.nextInt(password.length - 2);
            char character = password[index];
            password[index] = password[swapIndex];
            password[swapIndex] = character;
        }

        // Save and print the default password once for maintenance access.
        ADMIN_PWD_DEFAULT = password;
        LOG.warn("{}{}[MAINTENANCE MODE] No password was configured. Generated default maintenance password: {}{}{}",
                System.lineSeparator(), System.lineSeparator(), String.valueOf(password), System.lineSeparator(), System.lineSeparator());
    }


    private static char randomCharacter(String characters) {
        return characters.charAt(PASSWORD_RANDOM.nextInt(characters.length()));
    }


    private String getMaintenancePassword() {
        String configuredPassword = getConfiguredMaintenancePassword();
        if (configuredPassword != null) {
            return configuredPassword;
        }
        return ADMIN_PWD_DEFAULT == null ? null : String.valueOf(ADMIN_PWD_DEFAULT);
    }
}
