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
package ai.eldoc;

import ai.eldoc.context.ConfigContext;
import ai.eldoc.context.UserContext;
import ai.eldoc.model.WebSessionData;
import ai.eldoc.security.Identity;
import ai.eldoc.service.AppConfigService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Class holds list of all active sessions
 */
@Named
@ApplicationScoped
public class WebSessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(WebSessionManager.class);

    private static final String WEB_SESSION_LIST_KEY = "ai.eldoc.web.sessionList.";
    private static final String LICENSED_SESSION_LIST_KEY = "ai.eldoc.web.licensedSlotList.";

    @Inject
    private AppConfigService _appConfigService;

    @Inject
    private CacheManager _cacheManager;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private UserContext _userContext;

    private final Map<String, List<WebSessionData>> _cachedResultMap = new PassiveExpiringMap<>(10, TimeUnit.SECONDS, new ConcurrentHashMap<>());


    //
    // Constructor --------------------------------------------------------------------------------
    //
    @PostConstruct
    void init() {
        try {
            LOG.info("Found CachingProvider: {}", _cacheManager.getCachingProvider().getClass().getName());
            LOG.info("Found CacheManager: {}", _cacheManager.getClass().getName());
            _appConfigService.getTenantIdSet().forEach(this::initForTenantId);
        } catch (Exception e) {
            LOG.error(null, e);
        }
    }


    //
    // Methods  -----------------------------------------------------------------------------------
    //
    public void initForTenantId(String tenantId) {
        try {
            initWebSessionListCache(tenantId);
            initLicensedSessionListCache(tenantId);
        } catch (Exception e) {
            LOG.error("WebSessionManager initialization error for Tenant[{}]", tenantId, e);
        }
    }


    private void initWebSessionListCache(String tenantId) {
        Cache<String, WebSessionData> webSessionListCache = _cacheManager.getCache(WEB_SESSION_LIST_KEY + tenantId, String.class, WebSessionData.class);
        if (webSessionListCache == null) {
            // configure cache
            MutableConfiguration<String, WebSessionData> config = new MutableConfiguration<String, WebSessionData>().setTypes(String.class, WebSessionData.class);
            //config.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(Duration.ONE_MINUTE));
            //config.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(Duration.ONE_MINUTE));
            config.setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(Duration.ONE_DAY));

            // create cache
            _cacheManager.createCache(WEB_SESSION_LIST_KEY + tenantId, config);
        }
    }


    /**
     * Inits Licensed Sessions Map (for the Concurrent Licensing type)
     * Represented as Map<systemId, sessionId>
     * Where:
     * systemId - used for counting licensed sessions slots
     * sessionId - just a placeholder and keeps the latest active sessionId
     */
    private void initLicensedSessionListCache(String tenantId) {
        Cache<String, String> licensedSessionListCache = _cacheManager.getCache(LICENSED_SESSION_LIST_KEY + tenantId, String.class, String.class);
        if (licensedSessionListCache == null) {
            // configure cache
            MutableConfiguration<String, String> config = new MutableConfiguration<String, String>().setTypes(String.class, String.class);
            config.setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(Duration.ONE_HOUR));

            // create cache
            _cacheManager.createCache(LICENSED_SESSION_LIST_KEY + tenantId, config);
        }
    }


    /**
     * Adds web session data to the list of all sessions
     */
    public void addOrUpdateWebSession(String tenantId, Subject subject) {
        if (subject == null || tenantId == null) {
            return;
        }

        if (subject.getSession(false) == null || isBlank(String.valueOf(subject.getSession(false).getId()))) {
            return;
        }

        try {
            addOrUpdateWebSessionList(tenantId, String.valueOf(subject.getSession(false).getId()), subject);
            addOrUpdateLicensedSessionList(tenantId, String.valueOf(subject.getSession(false).getId()), subject);
        } catch (Exception e) {
            LOG.error("Tenant[{}] Subject principal: {}", tenantId, subject.getPrincipal(), e);
        }
    }


    private void addOrUpdateWebSessionList(String tenantId, String sessionId, Subject subject) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding or updating session ID {} in the WebSession slots list", sessionId);
        }

        // Add or Update session in the App Session List (distributed in order to support clustered deployment)
        Cache<String, WebSessionData> webSessionCache = _cacheManager.getCache(WEB_SESSION_LIST_KEY + tenantId, String.class, WebSessionData.class);
        if (webSessionCache.containsKey(sessionId)) {
            // We need to update cache value so ModifiedExpiryPolicy works properly
            webSessionCache.put(sessionId, createOrUpdateWithRequest(webSessionCache.get(sessionId), subject, tenantId));
        } else {
            webSessionCache.put(sessionId, createOrUpdateWithRequest(null, subject, tenantId));
        }
    }


    private void addOrUpdateLicensedSessionList(String tenantId, String sessionId, Subject subject) {
        // Process only sessions of authenticated users and in case Concurrent license type in used
        if (subject.getPrincipal() == null) {
            return;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding or updating session ID {} in the LicensedSession list", sessionId);
        }

        // Adds session to the App Licensed Session List (distributed in case of clustered deployment)
        String systemId = ((Identity) subject.getPrincipal()).getSystemId();
        if (!hasLicensedSessionSlot(tenantId, systemId)) {
            // We need to update cache value so ModifiedExpiryPolicy works properly
            _cacheManager.getCache(LICENSED_SESSION_LIST_KEY + tenantId, String.class, String.class).put(systemId, sessionId);
        }
    }


    /**
     * Removes session form the WebSession list
     */
    public void removeSession(HttpSession httpSession) {
        _appConfigService.getTenantIdSet().forEach(tenantId -> {
            try {
                TenantContext.setCurrentTenant(tenantId);
                removeSession(tenantId, httpSession);
            } finally {
                TenantContext.clear();
            }
        });
    }


    private void removeSession(String tenantId, HttpSession httpSession) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Tenant[{}] Removing session ID {} from the WebSession slots list", tenantId, httpSession.getId());
        }
        Cache<String, WebSessionData> cache = _cacheManager.getCache(WEB_SESSION_LIST_KEY + tenantId, String.class, WebSessionData.class);
        if (cache != null) {
            // This may happen for tenants which are still in the process of initialization
            cache.remove(httpSession.getId());
            removeLicensedSession(tenantId, httpSession.getId());
        }
    }


    /**
     * Removes Licensed session from the list
     */
    private void removeLicensedSession(String tenantId, String sessionId) {
        // we check for slots only when Concurrent licensing is set

        if (LOG.isTraceEnabled()) {
            LOG.trace("Tenant[{}] Removing session ID {} from the Licensed Sessions slots list", tenantId, sessionId);
        }

        Iterator<Cache.Entry<String, String>> it = _cacheManager.getCache(LICENSED_SESSION_LIST_KEY + tenantId, String.class, String.class).iterator();
        while (it.hasNext()) {
            Cache.Entry<String, String> entry = it.next();
            if (Objects.equals(entry.getValue(), sessionId)) {
                it.remove();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Tenant[{}] Releasing licensed slot for {} found by session ID {}", tenantId, entry.getKey(), entry.getValue());
                }
            }
        }
    }


    public void releaseLicenseSlot(String systemId) {
        releaseLicenseSlot(TenantContext.getCurrentTenantId(), systemId);
    }


    private void releaseLicenseSlot(String tenantId, String systemId) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Tenant[{}] Releasing licensed slot for {}", tenantId, systemId);
        }

        _cacheManager.getCache(LICENSED_SESSION_LIST_KEY + tenantId, String.class, String.class).remove(systemId);
    }


    private WebSessionData createOrUpdateWithRequest(WebSessionData webSessionData, Subject subject, String tenantId) {
        Session session = subject.getSession(false);
        if (webSessionData == null) {
            webSessionData = new WebSessionData(session.getId());
        }
        if (session.getStartTimestamp() != null) {
            webSessionData.setStartTimestamp(session.getStartTimestamp().getTime());
        }
        if (session.getLastAccessTime() != null) {
            webSessionData.setLastAccessed(session.getLastAccessTime().getTime());
        }
        webSessionData.setTimeout(session.getTimeout());
        if (subject.getPrincipal() instanceof Identity identity) {
            webSessionData.setSystemId(identity.getSystemId());
        } else {
            webSessionData.setSystemId(null);
        }
        webSessionData.setHost(session.getHost());
        webSessionData.setTenantId(tenantId);
        return webSessionData;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public List<WebSessionData> getSessionList() {
        String cacheId = _userContext.getId() + "@" + TenantContext.getCurrentTenantId();

        List<WebSessionData> result = _cachedResultMap.get(cacheId);
        if (result != null) {
            return result; // We got cached result - returning it
        }

        if (_userContext.isMaintAdmin()) {
            // Maint admin can see all sessions
            result = new ArrayList<>();
            for (String tenantId : _appConfigService.getTenantIdSet().stream().sorted().toList()) {
                result.addAll(getSessionList(tenantId));
            }
        } else {
            result = getSessionList(TenantContext.getCurrentTenantId());
        }

        _cachedResultMap.put(cacheId, result);
        return result;
    }


    private List<WebSessionData> getSessionList(String tenantId) {
        List<WebSessionData> result = new ArrayList<>();
        _cacheManager.getCache(WEB_SESSION_LIST_KEY + tenantId, String.class, WebSessionData.class).forEach(entry -> {
            result.add(entry.getValue());
        });
        return result;
    }


    /**
     * Returns list of licensed session IDs
     */
    public Collection<String> getLicensedSessionList() {
        return getLicensedSessionList(TenantContext.getCurrentTenantId());
    }


    private Collection<String> getLicensedSessionList(String tenantId) {
        List<String> result = new ArrayList<>();
        _cacheManager.getCache(LICENSED_SESSION_LIST_KEY + tenantId, String.class, String.class).forEach(entry -> {
            result.add(entry.getKey());
        });
        return result;
    }


    /**
     * Checks whether slots available for connection when Concurrent based License is installed
     */
    public boolean hasLicensedSessionSlot(String systemId) {
        return hasLicensedSessionSlot(TenantContext.getCurrentTenantId(), systemId);
    }


    private boolean hasLicensedSessionSlot(String tenantId, String systemId) {
        // we check for slots only when Concurrent licensing is set
        return _cacheManager.getCache(LICENSED_SESSION_LIST_KEY + tenantId, String.class, String.class).containsKey(systemId);
    }

}
