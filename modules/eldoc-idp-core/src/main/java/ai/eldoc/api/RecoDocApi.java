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

import ai.eldoc.dao.*;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.RecoData;
import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.ocr.OcrText;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.service.RecoWorkflowService;
import ai.eldoc.util.JsonbUtils;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.List;
import static ai.eldoc.api.RestApiUtils.buildErrorResponse;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * DocumentApi API for internal use
 */
@Path("/recoDoc")
@RequestScoped
public class RecoDocApi {

    private static final Logger LOG = LoggerFactory.getLogger(RecoDocApi.class);

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private RecoDataDao _recoDataDao;

    @Inject
    private RecoWorkflowService _recoWorkflowService;


    /**
     * Provides API for retrieving text-only content from RecoFile (IDP_PREPROCESSED)
     *
     * @param docId RecoWorkflow ID
     * @param dataId RecoData ID
     * @param pageIdx Page index
     * @return Text content of the requested page
     */
    @GET
    @Path("/{docId}/{dataId}/text/{pageIdx}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRecoFileTextOnlyByDocIdAndDataId(
            @PathParam("docId") @Pattern(regexp = "(?i)^[0-9abcdef]{24}$", message = "Param [docId] is not valid") String docId,
            @PathParam("dataId") @Pattern(regexp = "(?i)^[0-9abcdef]{24}$", message = "Param [dataId] is not valid") String dataId,
            @PathParam("pageIdx") @Min(0) @Max(Integer.MAX_VALUE) @DefaultValue("0") int pageIdx) {
        try (PDDocument document = getRequestedDoc(docId, dataId)) {
            return RestApiUtils.buildOkResponse(encodeDoc(document, pageIdx));
        } catch (Exception e) {
            LOG.error("RecoWorkflow[{}]: RecoData[{}]: Error retrieving TextOnly preview", docId, dataId, e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Please, contact site administrator");
        }
    }


    /**
     * Provides API for retrieving text-only content from RecoFile (IDP_PREPROCESSED)
     *
     * @param docId RecoWorkflow ID
     * @param pageIdx Page index
     * @return Text content of the requested page
     */
    @GET
    @Path("/{docId}/text/{pageIdx}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRecoFileTextOnlyByDocId(
            @PathParam("docId") @Pattern(regexp = "(?i)^[0-9abcdef]{24}$", message = "Param [docId] is not valid") String docId,
            @PathParam("pageIdx") @Min(0) @Max(Integer.MAX_VALUE) @DefaultValue("0") int pageIdx) {
        try (PDDocument document = getRequestedDoc(docId, null)) {
            if (document == null) {
                return RestApiUtils.buildOkNoContentResponse();
            }
            return RestApiUtils.buildOkResponse(encodeDoc(document, pageIdx));
        } catch (Exception e) {
            LOG.error("RecoWorkflow[{}]: Error retrieving TextOnly preview", docId, e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Please, contact site administrator");
        }
    }


    private PDDocument getRequestedDoc(String docId, String recoDataId) throws IOException {
        // Retrieve requested Workflow
        Workflow recoWF = _workflowDao.find(docId, SecurityUtils.getSubjectAclContext());
        RecoData recoData = null;
        if (isNotBlank(recoDataId)) {
            recoData = _recoDataDao.find(recoDataId);
        }

        // Retrieve required file from DB
        List<DbFile> previewFiles = _recoWorkflowService.getPreviewFiles(recoWF, recoData);
        DbFile previewFile = previewFiles.stream()
                .filter(fileItem -> fileItem.getDbFileRef().getTags().contains(RecoData.FILE_TAG.IDP_PREPROCESSED_FINAL.name()))
                .findFirst().orElse(null);
        if (previewFile == null) {
            previewFile = previewFiles.stream()
                    .filter(fileItem -> fileItem.getDbFileRef().getTags().contains(Workflow.FILE_TAG.IDP_PREPROCESSED.name()))
                    .findFirst().orElse(null);
            if (previewFile == null) {
                return null;
            }
        }
        try (DbFileDownloadStream downloadStream = DaoUtils.getFileAwareDao(previewFile.getDbFileRef()).getFilesDao().getFileDownloadStream(previewFile.getDbFileRef())) {
            return PdfUtils.loadPDF(downloadStream);
        }
    }


    private JsonObject encodeDoc(PDDocument document, int pageIdx) throws IOException {
        Rectangle pageDimensions = PdfUtils.getPageDimensionRect(document.getPage(pageIdx));
        OcrText pageText = PdfUtils.getTextWithMeta(document, pageIdx);

        // Add page dimensions to response
        JsonObjectBuilder pageDimsBuilder = JsonbUtils.createObjectBuilder()
                .add("x", pageDimensions.getX())
                .add("y", pageDimensions.getY())
                .add("w", pageDimensions.getWidth())
                .add("h", pageDimensions.getHeight());
        JsonObjectBuilder jsonBuilder = JsonbUtils.createObjectBuilder()
                .add("dims", pageDimsBuilder);

        // Add text data
        JsonArrayBuilder ocrDataBuilder = Json.createArrayBuilder();
        pageText.getOcrData().forEach(symbol -> {
            JsonObjectBuilder symbolRegionBuilder = JsonbUtils.createObjectBuilder()
                    .add("x", symbol.getRegion().getX())
                    .add("y", symbol.getRegion().getY())
                    .add("w", symbol.getRegion().getWidth())
                    .add("h", symbol.getRegion().getHeight());
            JsonObjectBuilder symbolBuilder = JsonbUtils.createObjectBuilder()
                    .add("region", symbolRegionBuilder)
                    .add("char", String.valueOf(symbol.getChar()))
                    .add("fontSize", symbol.getFontSizePt());
            ocrDataBuilder.add(symbolBuilder);
        });
        jsonBuilder.add("text", ocrDataBuilder);

        return jsonBuilder.build();
    }
}
