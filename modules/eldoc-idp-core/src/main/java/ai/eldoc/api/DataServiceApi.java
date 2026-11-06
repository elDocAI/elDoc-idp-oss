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

import ai.eldoc.dao.DaoUtils;
import ai.eldoc.dao.FileAwareDao;
import ai.eldoc.exceptions.RecordNotFoundInDbException;
import ai.eldoc.model.DbFileDownloadStream;
import ai.eldoc.model.DbWebRef;
import com.mongodb.MongoGridFSException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import static ai.eldoc.api.RestApiUtils.HEADER_SERVER;
import static ai.eldoc.api.RestApiUtils.HEADER_SERVER_VALUE;


/**
 * Data Service API
 */
@RequestScoped
@Path("/dataService")
public class DataServiceApi {

    private static final Logger LOG = LoggerFactory.getLogger(DataServiceApi.class);


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns requested file as OCTET_STREAM.<br>
     * !WARNING! Doesn't support revisions
     *
     * @param dbWebRef path pattern: /api/dataService/8hN09MdEuUrcdvujiMTn9AFDEbKcEK4Oyu (what is encrypted DbWebRef e.g.: tid1000-206-6376f38b9f293412c4ad73ac-0)
     * @return
     * @throws IOException
     * @deprecated Due to the fact that this method doesn't support revisionAwareDao and also doesn't perform ACL check on files from WorkflowData collection it has to be upgraded or replaced ASAP
     */
    @GET
    @Path("/{dbWebRef}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Deprecated
    public Response get(@PathParam("dbWebRef") DbWebRef dbWebRef) throws IOException {

        FileAwareDao fileAwareDao = DaoUtils.getFileAwareDao(dbWebRef);
        if (fileAwareDao == null) {
            LOG.warn("No FileAwareDao found by DbWebRef: {}", String.valueOf(dbWebRef));
            return RestApiUtils.buildErrorResponse(Response.Status.BAD_REQUEST, "Invalid reference");
        }

        try {
            DbFileDownloadStream downloadStream = fileAwareDao.getFilesDao().getFileDownloadStream(fileAwareDao.getFilesDao().createDbFileRef(dbWebRef.getIdAsString(), null));

            String fileNameEncoded = URLEncoder.encode(downloadStream.getDbFile().getFileName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            StreamingOutput streamingOutput = output -> {
                try (downloadStream) {
                    downloadStream.transferTo(output);
                    output.flush();
                }
            };

            return Response.ok(streamingOutput)
                    .header(HEADER_SERVER, HEADER_SERVER_VALUE)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileNameEncoded)
                    .type(RestApiUtils.getMediaType(downloadStream.getDbFile().getContentType()))
                    .encoding(StandardCharsets.UTF_8.name())
                    .header(HttpHeaders.CONTENT_LENGTH, downloadStream.getDbFile().getLength())
                    .build();

        } catch (RecordNotFoundInDbException | MongoGridFSException ex1) {
            // MongoGridFSException - According to the MongoDB driver source code this exception in this place could be only because document not found reason
            throw new NotFoundException("Record not found", ex1);
        }
    }
}
