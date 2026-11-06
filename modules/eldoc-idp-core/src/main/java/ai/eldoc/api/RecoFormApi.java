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

import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.exceptions.ScriptingException;
import ai.eldoc.exceptions.TimeoutException;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.RecoForm;
import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.ocr.OcrText;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.service.CodeSnippetService;
import ai.eldoc.service.RecoFormService;
import ai.eldoc.util.JsonbUtils;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.glassfish.jersey.media.multipart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import static ai.eldoc.api.RestApiUtils.buildErrorResponse;
import static ai.eldoc.api.RestApiUtils.buildOkResponse;
import static org.apache.commons.io.FileUtils.deleteQuietly;


/**
 * RecoForm REST API
 */
@Path("/recoForm")
@RequestScoped
public class RecoFormApi {

    private static final Logger LOG = LoggerFactory.getLogger(RecoFormApi.class);

    @Inject
    private RecoFormService _recoFormService;

    @Inject
    private CodeSnippetService _codeSnippetService;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * RecoForm Import API service
     *
     * @param request
     * @param uriInfo
     * @param rfsFile
     * @return
     */
    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresRoles(DirectoryConstants.ROLE_ADMINDOCRW)
    public Response postRecoFormImport(@Context HttpServletRequest request,
            @Context UriInfo uriInfo,
            @FormDataParam("rfsFile") FormDataBodyPart rfsFile) {

        File tempImportFile = null;
        try {
            if (rfsFile == null) {
                return buildErrorResponse(Response.Status.BAD_REQUEST, "RFS file body part was not provided");
            }

            // Save locally uploaded file
            tempImportFile = File.createTempFile("recoForm_import_", ".rfs");
            BodyPartEntity bodyPartEntity = (BodyPartEntity) rfsFile.getEntity();
            try (InputStream is = bodyPartEntity.getInputStream();) {
                Files.copy(is, tempImportFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            List<java.nio.file.Path> recoFormsImportList = new ArrayList<>();
            List<RecoForm> skippedForms = new ArrayList<>();

            _recoFormService.importRecoForms(tempImportFile, recoFormsImportList, skippedForms);

            JsonObjectBuilder resultBuilder = Json.createObjectBuilder();
            resultBuilder.add("success", "true");
            resultBuilder.add("total", recoFormsImportList.size());
            resultBuilder.add("skipped", skippedForms.size());
            if (!skippedForms.isEmpty()) {
                JsonArrayBuilder skippedFormsArr = Json.createArrayBuilder();
                skippedForms.forEach(recoForm -> skippedFormsArr.add(recoForm.getName()));
                resultBuilder.add("skippedList", skippedFormsArr.build());
            }

            return buildOkResponse(resultBuilder.build());
        } catch (UnsupportedOperationException ex1) {
            LOG.debug("{}: {}", request.getMethod(), uriInfo.getRequestUri().toString(), ex1);
            return buildErrorResponse(Response.Status.BAD_REQUEST, "RFS file is not valid, please upload a valid file");
        } catch (ScriptingException | TimeoutException ex2) {
            LOG.debug("{}: {}", request.getMethod(), uriInfo.getRequestUri().toString(), ex2);
            return buildErrorResponse(Response.Status.BAD_REQUEST, "RFS file contains code library with errors, please upload a valid file");
        } catch (Exception e) {
            LOG.error("{}: {}", request.getMethod(), uriInfo.getRequestUri().toString(), e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Please, contact site administrator");
        } finally {
            // Delete local copy of the uploaded file once all is processed
            if (tempImportFile != null) {
                if (!deleteQuietly(tempImportFile)) {
                    LOG.warn("Cannot delete temp file: {}", tempImportFile.getAbsolutePath());
                }
            }
        }
    }


    /**
     * Method used for automatic upload and activation of RFS while automatic testing
     *
     * @param request
     * @param uriInfo
     * @param rfsFile
     * @param existingAction
     * @param uploadedAction
     * @return
     */
    @POST
    @Path("/import/auto")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresRoles(DirectoryConstants.ROLE_ADMINDOCRW)
    public Response postRecoFormImportAutomation(@Context HttpServletRequest request,
            @Context UriInfo uriInfo,
            @FormDataParam("rfsFile") FormDataBodyPart rfsFile,
            @QueryParam("existingAction") @Pattern(regexp = "^suspend|delete$", message = "The [existingAction] param not valid") String existingAction,
            @QueryParam("uploadedAction") @Pattern(regexp = "^activate$", message = "The [uploadedAction] param not valid") String uploadedAction) {

        File tempImportFile = null;
        try {
            if (rfsFile == null) {
                return buildErrorResponse(Response.Status.BAD_REQUEST, "RFS file body part was not provided");
            }

            // Save locally uploaded file
            tempImportFile = File.createTempFile("recoForm_import_", ".rfs");
            BodyPartEntity bodyPartEntity = (BodyPartEntity) rfsFile.getEntity();
            try (InputStream is = bodyPartEntity.getInputStream();) {
                Files.copy(is, tempImportFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // If request has 'existing action' param - perform requested action
            if ("suspend".equals(existingAction)) {
                _recoFormService.suspendFormBatch(_recoFormService.findAll());
            } else if ("delete".equals(existingAction)) {
                _recoFormService.deleteFormBatch(_recoFormService.findAll());
                List<CodeSnippet> codeSnippetList = _codeSnippetService.findByType(CodeSnippet.TYPE_RECO);
                if (!codeSnippetList.isEmpty()) {
                    _codeSnippetService.delete(codeSnippetList.get(0));
                }
            }

            List<java.nio.file.Path> recoFormsImportList = new ArrayList<>();
            List<RecoForm> skippedForms = new ArrayList<>();
            int activated = 0;

            List<RecoForm> importedForms = _recoFormService.importRecoForms(tempImportFile, recoFormsImportList, skippedForms);

            if ("activate".equals(uploadedAction)) {
                activated = _recoFormService.activateFormBatch(importedForms);
            }

            JsonObjectBuilder resultBuilder = Json.createObjectBuilder();
            resultBuilder.add("success", "true");
            resultBuilder.add("total", recoFormsImportList.size());
            resultBuilder.add("skipped", skippedForms.size());
            resultBuilder.add("activated", activated);
            if (!skippedForms.isEmpty()) {
                JsonArrayBuilder skippedFormsArr = Json.createArrayBuilder();
                skippedForms.forEach(recoForm -> skippedFormsArr.add(recoForm.getName()));
                resultBuilder.add("skippedList", skippedFormsArr.build());
            }

            return buildOkResponse(resultBuilder.build());
        } catch (UnsupportedOperationException ex1) {
            LOG.debug("{}: {}", request.getMethod(), uriInfo.getRequestUri().toString(), ex1);
            return buildErrorResponse(Response.Status.BAD_REQUEST, "RFS file is not valid, please upload a valid file");
        } catch (ScriptingException | TimeoutException ex2) {
            LOG.debug("{}: {}", request.getMethod(), uriInfo.getRequestUri().toString(), ex2);
            return buildErrorResponse(Response.Status.BAD_REQUEST, "RFS file contains code library with errors, please upload a valid file");
        } catch (Exception e) {
            LOG.error("{}: {}", request.getMethod(), uriInfo.getRequestUri().toString(), e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Please, contact site administrator");
        } finally {
            // Delete local copy of the uploaded file once all is processed
            if (tempImportFile != null) {
                if (!deleteQuietly(tempImportFile)) {
                    LOG.warn("Cannot delete temp file: {}", tempImportFile.getAbsolutePath());
                }
            }
        }
    }


    /**
     * Provides API for retrieving text-only content from RecoForm file (PREPROCESSED)
     *
     * @param id
     * @param pageIdx
     * @return
     */
    @GET
    @Path("/{id}/text/{pageIdx}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRecoFileTextOnlyByDocId(
            @PathParam("id") @Pattern(regexp = "(?i)^[0-9abcdef]{24}$", message = "Param [id] is not valid") String id,
            @PathParam("pageIdx") @Min(0) @Max(Integer.MAX_VALUE) @DefaultValue("0") int pageIdx) {
        try {
            // Retrieve requested Workflow
            RecoForm recoForm = _recoFormService.findById(id);

            // Retrieve required file from DB
            DbFileRef previewFile = recoForm.getFilesByTag(RecoForm.FILE_TAG.PREPROCESSED.name()).get(0);
            if (previewFile == null) {
                return null;
            }

            try (DbFileDownloadStream downloadStream = _recoFormService.getFile(previewFile); PDDocument document = PdfUtils.loadPDF(downloadStream)) {
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

                return RestApiUtils.buildOkResponse(jsonBuilder.build());
            }
        } catch (Exception e) {
            LOG.error("id={}", id, e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Please, contact site administrator");
        }
    }

}
