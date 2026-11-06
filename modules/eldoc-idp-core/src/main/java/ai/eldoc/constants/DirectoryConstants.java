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
package ai.eldoc.constants;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Directory constants
 */
public final class DirectoryConstants {

    //
    // BPM systemId constants
    //
    /**
     * Replaced in processes with Initiator
     */
    public static final String BPM_PERS_INITIATOR = "cn=initiator/ou=bpm/o=dms";
    /**
     * Replaced in processes with chief of Initiator's unit
     */
    public static final String BPM_PERS_INITIATORCHIEF = "cn=initiatorchief/ou=bpm/o=dms";
    /**
     * Initiator's Unit
     */
    public static final String BPM_UNIT_INITIATOR = "bpm-unt-initiator";
    /**
     * Initiator's group tagged with a given tag
     */
    public static final String BPM_GRP_TAG_PREFIX = "bpm-grp-tag:";

    //
    // System systemId constants
    //
    public static final String ELDOC_ROBOT = "cn=eldocrobot/o=dms";
    public static final String ELDOC_RECO_BOT = "usr-eldoc-reco-bot";
    public static final String ELDOC_CONVERTER_BOT = "usr-eldoc-conv-bot";
    public static final String ELDOC_ESIGN_BOT = "usr-eldoc-esign-bot";
    public static final String MAINTENANCE_USER_SYSTEMID = "usr-eldoc-maint-admin";
    public static final String ANONYMOUS = "anonymous";
    public static final Set<String> SYSTEM_USERS_ID_LIST = Set.of(ELDOC_ROBOT, ELDOC_RECO_BOT, ELDOC_CONVERTER_BOT, ELDOC_ESIGN_BOT, MAINTENANCE_USER_SYSTEMID, ANONYMOUS);

    //
    // Roles constants
    //
    public static final String ROLE_ADMINDOCR = "[elAdminDocsR]"; // Docs Admin (reader): can admin docs (view) and workflows (view)
    public static final String ROLE_ADMINDOCRW = "[elAdminDocsRW]"; // Docs Admin: can admin docs (change) and workflows (create, change, hide)
    /**
     * Role gives right for deleting documents from the system
     * Important: this role is not a fully independent role as it can be used only with the roles: [elAdminDocsR] or [elAdminDocsRW]
     */
    public static final String ROLE_ADMINDOCD = "[elAdminDocsD]"; // Docs Admin: can admin docs (delete) and workflows (delete)
    public static final Set<String> ROLE_ADMINDOC_ALL = Set.of(ROLE_ADMINDOCR, ROLE_ADMINDOCRW, ROLE_ADMINDOCD);

    public static final String ROLE_ADMINSYSR = "[elAdminSysR]"; // Sys Admin: manage Users, manage elDoc Config
    public static final String ROLE_ADMINSYSRW = "[elAdminSysRW]"; // Sys Admin: manage Users, manage elDoc Config
    public static final Set<String> ROLE_ADMINSYS_ALL = Set.of(ROLE_ADMINSYSR, ROLE_ADMINSYSRW);

    public static final String ROLE_MAINT_ADMIN = "[mntAdm]"; // Maintanence Admin Role

    /**
     * All Admin Roles collection
     */
    public static final Set<String> ROLE_ADMINS_All = Stream.of(ROLE_ADMINDOC_ALL, ROLE_ADMINSYS_ALL).flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());

    /**
     * [elUser] Role. By default any user in elDoc - has role [elUser]
     */
    public static final String ROLE_ALLUSERS = "[elUser]";

    /**
     * All system Roles collection
     */
    public static final Set<String> ROLE_All = Stream.concat(ROLE_ADMINS_All.stream(), Stream.of(ROLE_ALLUSERS)).collect(Collectors.toUnmodifiableSet());

    /**
     * Anonymous user
     */
    public static final int USER_TYPE_ANONYNOUS = 0;

    /**
     * Internal user, meaning user present in the local directory.
     * Do not mix up with user directory type, which can be also internal and external, but that only for the 1 - Internal user
     */
    public static final int USER_TYPE_INTERNAL = 1;

    /**
     * External user, invited to the collaboration via email for example, or sharable link.
     * Do not mix up with user directory type, which can be also internal and external, but that only for the 1 - Internal user
     *
     */
    public static final int USER_TYPE_EXTERNAL = 2;

}
