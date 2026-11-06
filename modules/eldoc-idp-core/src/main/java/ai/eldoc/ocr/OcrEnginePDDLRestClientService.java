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
package ai.eldoc.ocr;

import ai.eldoc.TenantContext;
import ai.eldoc.rest.*;
import ai.eldoc.rest.ResponseProcessingException;
import ai.eldoc.service.AppConfigService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.*;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * PaddleOCR OcrEngine Rest Client Service
 */
@ApplicationScoped
public class OcrEnginePDDLRestClientService {

    private static final Logger LOG = LoggerFactory.getLogger(OcrEnginePDDLRestClientService.class);

    @Inject
    private AppConfigService _appConfigService;

    private Client _rsClient = null;
    private WebTarget _rsTarget = null;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Initializes on first access
     */
    @PostConstruct
    public void postConstrcut() {
        LOG.info("OcrEnginePDDLRestClientService: Initialization started");
        init();
        LOG.info("OcrEnginePDDLRestClientService: Initialization done");
    }


    /**
     * Destroys this instance and closes all resources on context closure
     */
    @PreDestroy
    public void preDestroy() {
        destroy();
    }


    /**
     * Initializes Rest Client Service
     */
    public void init() {
        destroy();

        if (!Strings.CI.equals(_appConfigService.getIdpConfig().getOcrType(TenantContext.getCurrentTenantId()), OcrEngine.TYPE.PDDL.name())) {
            LOG.info("OcrEnginePDDLRestClientService: Skipping OCR engine initialization as not configured as OCR engine");
            return;
        }

        _rsClient = ClientBuilder.newClient();
        _rsTarget = _rsClient.target(_appConfigService.getIdpConfig().getOcrUrl());
    }


    /**
     * Destroys this instance and closes all resources
     */
    public void destroy() {
        try {
            if (_rsClient != null) {
                _rsClient.close();
                _rsClient = null;
                _rsTarget = null;
            }
        } catch (Exception e) {
            LOG.warn("Error occurred closing RS Client", e);
        }
    }


    public Response doPost(Object entity) throws RestClientException {
        try {
            return _rsTarget
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(entity, MediaType.APPLICATION_JSON));
        } catch (Throwable e) {
            // To avoid EJB roll-back handling, we wrap all execptions
            throw new RestClientException(e);
        }
    }


    /**
     * Sends POST request
     *
     * @param <T> Entity class that is expected to be returned by the endpoint
     * @param endpoint Endpoint to send a request to. Relative to Paddle API baseUrl, must not contain the leading slash
     * @param entity Entity, that should be sent to endpoint as a request body
     * @param expectedResponseCode HTTP Status Code that is expected to be returned by endpoint in case of successful request processing
     * @param expectedEntityType {@link GenericType} for entity, that is expected to be returned by endpoint as response body
     * @return Endpoint response entity
     * @throws ResponseStatusCodeException
     * @throws ResponseProcessingException
     * @throws RestClientException
     */
    public <T> T doPost(String endpoint, Object entity, int expectedResponseCode, GenericType<T> expectedEntityType)
            throws ResponseStatusCodeException, ResponseProcessingException, RestClientException {
        try {
            Response rsResponse = _rsTarget
                    .path(endpoint)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(entity, MediaType.APPLICATION_JSON));

            if (rsResponse.getStatus() != expectedResponseCode) {
                throw new ResponseStatusCodeException().setStatusCode(rsResponse.getStatus());
            }

            return rsResponse.readEntity(expectedEntityType);
        } catch (Throwable e) {
            // To avoid EJB roll-back handling, we wrap all exceptions
            throw new RestClientException(e);
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public boolean isInited() {
        return _rsClient != null && _rsTarget != null;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    public static OcrEnginePDDLRestClientService getInstance() {
        return CDI.current().select(OcrEnginePDDLRestClientService.class).get();
    }
}
