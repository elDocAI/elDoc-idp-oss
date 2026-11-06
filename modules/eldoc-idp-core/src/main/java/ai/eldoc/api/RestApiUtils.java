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
package ai.eldoc.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * REST API Utility Class
 */
@ApplicationScoped
public class RestApiUtils {

    // Response Headers
    public static final String HEADER_ELDOC_TOTAL_COUNT = "eldoc-total-count";
    public static final String HEADER_ELDOC_ERROR = "eldoc-error"; // Used with 4xx/5xx codes
    public static final String HEADER_ELDOC_ERROR_CODE = "eldoc-error-code"; // Used with 4xx/5xx codes and contains internal code of the error like: idXXXXX
    public static final String HEADER_SERVER = "Server";
    public static final String HEADER_SERVER_VALUE = "elDoc";
    public static final String HEADER_USER_AGENT = "User-Agent";


    //
    // Static Methods -----------------------------------------------------------------------------
    //
    public static Response buildOkNoContentResponse() {
        return Response
                .noContent()
                .header(HEADER_SERVER, HEADER_SERVER_VALUE)
                .build();
    }


    public static Response buildOkResponse(Object entity) {
        return Response
                .ok(entity)
                .header(HEADER_SERVER, HEADER_SERVER_VALUE)
                .build();
    }


    public static Response buildOkResponse(List<?> entity, long total) {
        return Response
                .ok(entity)
                .header(HEADER_ELDOC_TOTAL_COUNT, total)
                .header(HEADER_SERVER, HEADER_SERVER_VALUE)
                .build();
    }


    /**
     * Response with file entity
     *
     * @param entity
     * @param fileName fileName (plain, not encoded, this method performs encoding on its own)
     * @param contentLength
     * @return
     */
    public static Response buildOkFileDownloadResponse(Object entity, String fileName, long contentLength) {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return Response
                .ok(entity)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''".concat(encodedFileName))
                .header(HEADER_SERVER, HEADER_SERVER_VALUE)
                .header(HttpHeaders.CONTENT_LENGTH, contentLength)
                .build();
    }


    /**
     * Response with file entity
     *
     * @param entity
     * @param fileName fileName (plain, not encoded, this method performs encoding on its own)
     * @return
     */
    public static Response buildOkFileDownloadResponse(Object entity, String fileName) {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return Response
                .ok(entity)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''".concat(encodedFileName))
                .header(HEADER_SERVER, HEADER_SERVER_VALUE)
                .build();
    }


    public static Response buildErrorResponse(int statusId, Object entity, String message) {
        return Response
                .status(statusId)
                .entity(entity)
                .header(HEADER_ELDOC_ERROR, message)
                .header(HEADER_SERVER, HEADER_SERVER_VALUE)
                .build();
    }


    /**
     * Returns Error Response
     *
     * @param status response status
     * @param message error message
     * @return
     */
    public static Response buildErrorResponse(Response.Status status, String message) {
        return Response
                .status(status)
                .entity("") // use .entity("") so it returns empty page instead of default page 404.xhtml page in case of 404 response for example
                .header(HEADER_ELDOC_ERROR, message)
                .header(HEADER_SERVER, HEADER_SERVER_VALUE)
                .build();
    }


    /**
     * Returns Error Response
     *
     * @param status response status
     * @param message error message
     * @param errorCode internal error code lice idXXXXX
     * @return
     */
    public static Response buildErrorResponse(Response.Status status, String message, String errorCode) {
        return Response
                .status(status)
                .entity("") // use .entity("") so it returns empty page instead of default page 404.xhtml page in case of 404 response for example
                .header(HEADER_ELDOC_ERROR, message)
                .header(HEADER_ELDOC_ERROR_CODE, errorCode)
                .header(HEADER_SERVER, HEADER_SERVER_VALUE)
                .build();
    }


    /**
     * Returns MediaType, created based on provided contentType.
     * If no content type provided - uses wildcard content-type.
     * If provided content type is not valid - uses wildcard content-type
     *
     * @param contentType
     * @return
     */
    public static MediaType getMediaType(String contentType) {

        // If no contentType provided - use default
        // If content type doesn't contain separator '/' - use wildcart content type
        if (isBlank(contentType) || !contentType.contains("/")) {
            return MediaType.WILDCARD_TYPE.withCharset(StandardCharsets.UTF_8.name());
        }

        String[] contentTypeComponents = contentType.split("/");

        // If provided content type doesn't contain 2 components or any of it is empty - use wildcart content type
        if (contentTypeComponents.length != 2 || isBlank(contentTypeComponents[0]) || isBlank(contentTypeComponents[1])) {
            return MediaType.WILDCARD_TYPE.withCharset(StandardCharsets.UTF_8.name());
        }

        return new MediaType(contentTypeComponents[0], contentTypeComponents[1], StandardCharsets.UTF_8.name());
    }
}
