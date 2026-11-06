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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.constants.FileTypes;
import ai.eldoc.constants.Preview;
import ai.eldoc.context.UserContext;
import ai.eldoc.dao.DaoUtils;
import ai.eldoc.model.*;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.reco.ImageProcessor;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.util.MimeType;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.apache.commons.io.IOUtils;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;
import static ai.eldoc.api.RestApiUtils.*;


/**
 * Preview Service for providing on-line preview functionality
 */
@Path("/preview")
@RequestScoped
public class PreviewApi {

    private static final Logger LOG = Logger.getLogger(PreviewApi.class.getName());

    @Inject
    private UserContext _userContext;


    //
    // Class Methods ------------------------------------------------------------------------------
    //


    /**
     * Prepares and returns response with the requested preview type
     *
     * @param request
     * @param previewId
     * @param pageNumber
     * @param keep
     * @return
     * @throws java.io.IOException
     */
    @GET
    @Produces({MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
    public Response getPreview(
            @Context HttpServletRequest request,
            @QueryParam("id") @Pattern(regexp = "[\\w-]{10,40}", message = "Param [id] is not valid") String previewId,
            @QueryParam("p") @DefaultValue("0") int pageNumber,
            @QueryParam("keep") @DefaultValue("false") boolean keep) throws IOException {

        PreviewItem previewItem;
        if (keep) {
            previewItem = _userContext.getPreviewItem(previewId);
        } else {
            previewItem = _userContext.getAndRemovePreviewItem(previewId);
        }
        if (previewItem == null) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "Preview item not found");
        }

        AclContext aclContext = SecurityUtils.getSubjectAclContext();

        return getPreview(previewItem.getFile().getDbFileRef(), pageNumber, previewItem.getPreviewType(), UserRequestMeta.fromRequest(request), aclContext);
    }


    /**
     * Prepares and returns response with the requested preview item RAW content
     *
     * @param previewId preview item id
     * @param keep whether to keep the preview item in the user context
     * @return Preview item content
     */
    @GET
    @Path("/content")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
    public Response getPreviewFileContent(
            @QueryParam("id") @Pattern(regexp = "[\\w-]{10,40}", message = "Param [id] is not valid") String previewId,
            @QueryParam("keep") @DefaultValue("false") boolean keep) {

        PreviewItem previewItem;
        if (keep) {
            previewItem = _userContext.getPreviewItem(previewId);
        } else {
            previewItem = _userContext.getAndRemovePreviewItem(previewId);
        }
        if (previewItem == null) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "Preview item not found");
        }

        DbFileDownloadStream fileStream = DaoUtils.getFileAwareDao(previewItem.getFile().getDbFileRef()).getFilesDao().getFileDownloadStream(previewItem.getFile().getDbFileRef());
        StreamingOutput output = fileDownloadStream -> {
            try (fileStream) {
                IOUtils.copy(fileStream, fileDownloadStream);
                fileDownloadStream.flush();
            }
        };
        return RestApiUtils.buildOkFileDownloadResponse(output, previewItem.getFile().getFileName(), previewItem.getFile().getLength());
    }


    /**
     * Prepares and returns response with the requested preview type
     *
     * @param dbFileRef
     * @param pageNumber
     * @param previewType
     * @param requestMeta
     * @param aclContext
     * @return
     * @throws IOException
     */
    public Response getPreview(DbFileRef dbFileRef, int pageNumber, int previewType, UserRequestMeta requestMeta, AclContext aclContext) throws IOException {
        return getPreview(dbFileRef, pageNumber, previewType, PdfUtils.IMAGE_RENDER_WEB_DPI, requestMeta, aclContext);
    }


    /**
     * Prepares and returns response with the requested preview type
     *
     * @param dbFileRef
     * @param pageNumber
     * @param previewType
     * @param dpi DPI, that should be used in case of rendering Image from office-type files
     * @return
     * @throws IOException
     */
    private Response getPreview(DbFileRef dbFileRef, int pageNumber, int previewType, int dpi, UserRequestMeta requestMeta, AclContext aclContext) throws IOException {

        // Build proper response meta (file type, filename, content type) based on file Metadata from DB and requested preview type
        String fileName;
        int fileType;
        String contentType;

        try (DbFileDownloadStream downloadStream = DaoUtils.getFileAwareDao(dbFileRef).getFilesDao().getFileDownloadStream(dbFileRef)) {
            fileName = downloadStream.getDbFile().getFileName();
            fileType = FileTypes.getFileType(fileName);

            switch (fileType) {
                case FileTypes.IMAGE -> {
                    boolean isTiff = FileTypes.isTiffFile(fileName);
                    contentType = isTiff ? MimeType.IMAGE_JPEG : downloadStream.getDbFile().getContentType();
                }
                case FileTypes.OFFICE -> {
                    boolean wantsPdf = previewType == Preview.TYPE_DOC_AS_PDF || previewType == Preview.TYPE_NATIVE;
                    contentType = wantsPdf ? MimeType.APPLICATION_PDF : MimeType.IMAGE_JPEG;
                }
                default -> {
                    String msg = String.format("File type not supported for preview: %s (type=%d)", fileName, fileType);
                    return Response.status(Response.Status.BAD_REQUEST).entity(msg).build();
                }
            }
        }

        // Create the StreamingOutput that re-opens the stream and performs actual preview image extraction/rendering
        StreamingOutput stream = out -> {
            try (DbFileDownloadStream downloadStream = DaoUtils.getFileAwareDao(dbFileRef).getFilesDao().getFileDownloadStream(dbFileRef)) {
                InputStream pdfIn = null;
                boolean closePdfIn = false;

                switch (fileType) {
                    case FileTypes.IMAGE:
                        if (FileTypes.isTiffFile(fileName)) {
                            BufferedImage img = ImageProcessor.readImageFilePage(downloadStream, pageNumber, null);
                            ImageProcessor.saveToJpg(img, out, 0.90f);
                            img.flush();
                            img = null;
                        } else {
                            IOUtils.copy(downloadStream, out);
                        }
                        break;

                    case FileTypes.OFFICE:
                        // 2a) get a PDF InputStream (either direct or via conversion)
                        if (PdfUtils.isPdf(fileName)) {
                            pdfIn = downloadStream;
                        } else {
                            throw new ElDocRuntimeException("Unsupported preview file type");
                        }

                        // 2b) finally render as PDF or images
                        if (previewType == Preview.TYPE_DOC_AS_PDF || previewType == Preview.TYPE_NATIVE) {
                            IOUtils.copy(pdfIn, out);
                        } else {
                            BufferedImage img = PdfUtils.renderPageToImage(pdfIn, pageNumber, dpi);
                            ImageProcessor.saveToJpg(img, out, 0.75f);
                            img.flush();
                            img = null;
                        }
                        break;
                }

                out.flush();
                if (closePdfIn && pdfIn != null) {
                    pdfIn.close();
                }
            }
        };

        // Build the response, setting dynamic contentType + headers
        return Response
                .ok(stream)
                .type(contentType)
                .header(HEADER_SERVER, HEADER_SERVER_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "no‑cache")
                .header(HttpHeaders.EXPIRES, -1)
                .build();
    }

}
