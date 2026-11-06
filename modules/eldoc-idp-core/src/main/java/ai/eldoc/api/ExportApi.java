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
import ai.eldoc.TenantContext;
import ai.eldoc.dao.*;
import ai.eldoc.data.DataType;
import ai.eldoc.data.WorkflowLazyQuery;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.RecoData;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.service.*;
import ai.eldoc.ui.converter.WorkflowStateConverter;
import ai.eldoc.util.*;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.*;
import org.apache.commons.csv.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static ai.eldoc.api.RestApiUtils.*;
import static ai.eldoc.reco.AbstractRecoFieldProcessor.DEFAULT_IMAGE_FIELD_FORMAT;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * ExportApi REST API
 */
@RequestScoped
@Path("/export")
public class ExportApi {

    private static final Logger LOG = LoggerFactory.getLogger(ExportApi.class);

    private static final String ELDOC_JVM_OPTION_EXPORT_MAX_DOCS = "ai.eldoc.export.maxdocs";
    private final static int EXPORT_MAX_DOCS = Integer.getInteger(ELDOC_JVM_OPTION_EXPORT_MAX_DOCS, 100000);

    private static final String ELDOC_JVM_OPTION_EXPORT_MAX_DOCS_CSV_WITHOUT_ARCHIVING = "ai.eldoc.export.maxdocs.csv";
    public final static int EXPORT_MAX_DOCS_CSV_WITHOUT_ARCHIVING = Integer.getInteger(ELDOC_JVM_OPTION_EXPORT_MAX_DOCS_CSV_WITHOUT_ARCHIVING, 10000);

    private final String TMP_DIR = System.getProperty("java.io.tmpdir");
    public static final String EXPORT_FILE_NAME_PREFIX = "elDoc_export_";
    public static final String EXPORT_FILE_NAME_DATE_FORMAT = "yyMMdd_HHmm";

    private static final String FIELD_ERROR_VALUE = "-err-";

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private RecoDataDao _recoDataDao;

    @Inject
    private RecoWorkflowService _recoWorkflowService;

    @Inject
    private TaskService _taskService;

    @Inject
    private FilesCleanupService _filesCleanupService;

    @Inject
    private FormFieldsUtils _formFieldsUtils;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * REST GET Methods for processing export tasks
     *
     * @param asyncResponse
     * @param exportTaskId
     */
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public void getAsync(@Suspended final AsyncResponse asyncResponse,
                         @QueryParam("id") @Pattern(regexp = "^[\\w-]{36}$", message = "The id is not valid") String exportTaskId) {

        // See more: https://blog.allegro.tech/2014/10/async-rest.html
        ExecutorService executor = Executors.newCachedThreadPool();
        final String tenantId = TenantContext.getCurrentTenantId();

        Runnable exportTask = () -> {
            try {
                TenantContext.setCurrentTenant(tenantId);
                String fileBaseName = EXPORT_FILE_NAME_PREFIX.concat((new SimpleDateFormat(EXPORT_FILE_NAME_DATE_FORMAT)).format(new Date()));

                WorkflowLazyQuery workflowLazyQuery = _taskService.getExportTask(exportTaskId);
                if (workflowLazyQuery == null) {
                    LOG.warn("WorkflowLazyQuery is null by the provided export task ID={}", exportTaskId);
                    asyncResponse.resume(buildErrorResponse(Response.Status.BAD_REQUEST, "No query for export provided"));
                    return;
                }

                // Get & remove localization settings (these parameters are always added while export task creation)
                String lang = (String) workflowLazyQuery.getFilter().remove(WorkflowLazyQuery.PARAM_LANG);
                String dateFormat = (String) workflowLazyQuery.getFilter().remove(WorkflowLazyQuery.PARAM_DATE_FORMAT);
                String timeFormat = (String) workflowLazyQuery.getFilter().remove(WorkflowLazyQuery.PARAM_TIME_FORMAT);
                String timeZone = (String) workflowLazyQuery.getFilter().remove(WorkflowLazyQuery.PARAM_TIMEZONE);
                boolean withAttachments = (boolean) workflowLazyQuery.getFilter().remove(WorkflowLazyQuery.PARAM_EXPORT_ATTACHMENTS);

                String fileName;
                if (withAttachments || workflowLazyQuery.getPageSize() > EXPORT_MAX_DOCS_CSV_WITHOUT_ARCHIVING) {
                    fileName = fileBaseName.concat(".zip");
                } else {
                    fileName = fileBaseName.concat(".csv");
                }

                StreamingOutput output = streamingOut -> {
                    if (withAttachments || workflowLazyQuery.getPageSize() > EXPORT_MAX_DOCS_CSV_WITHOUT_ARCHIVING) {
                        try (ZipOutputStream zos = new ZipOutputStream(streamingOut)) {
                            exportWorkflowsToCsv(workflowLazyQuery, zos, lang, dateFormat, timeFormat, timeZone, withAttachments, fileBaseName.concat(".csv"));
                            zos.flush();
                        }
                    } else {
                        exportWorkflowsToCsv(workflowLazyQuery, streamingOut, lang, dateFormat, timeFormat, timeZone, withAttachments, fileBaseName.concat(".csv"));
                    }
                };

                asyncResponse.resume(RestApiUtils.buildOkFileDownloadResponse(output, fileName));

            } catch (Exception ex) {
                LOG.error("id=" + exportTaskId, ex);
                asyncResponse.resume(RestApiUtils.buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Please, contact site administrator"));
            }
        };

        exportTask = SecurityUtils.getSubject().associateWith(exportTask);
        executor.execute(exportTask);

        executor.shutdown();
    }


    @GET
    @Path("/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public void getDownloadAsync(
            @Suspended final AsyncResponse asyncResponse,
            @QueryParam("id") @Pattern(regexp = "^[\\w-]{36}$", message = "The id is not valid") String exportDownloadTaskId) {

        ExecutorService executor = Executors.newCachedThreadPool();
        final String tenantId = TenantContext.getCurrentTenantId();

        Runnable downloadTask = () -> {
            try {
                TenantContext.setCurrentTenant(tenantId);
                File downloadFile = new File(_taskService.getExportDownloadTask(exportDownloadTaskId));
                String fileNameEncoded = URLEncoder.encode(downloadFile.getName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");

                asyncResponse.resume(Response.ok(downloadFile).
                        header(HEADER_SERVER, HEADER_SERVER_VALUE).
                        header(HttpHeaders.CACHE_CONTROL, "no-cache").
                        header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''".concat(fileNameEncoded)).
                        build());

                try {
                    downloadFile.delete();
                } catch (Exception ex1) {
                    LOG.warn(downloadFile.getAbsolutePath(), ex1);
                }

            } catch (Exception e) {
                LOG.error("id={}", exportDownloadTaskId, e);
                asyncResponse.resume(buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Please, contact site administrator"));
            }
        };

        downloadTask = SecurityUtils.getSubject().associateWith(downloadTask);
        executor.execute(downloadTask);

        executor.shutdown();
    }


    /**
     * Creates CSV export file and writes it to ZIP
     *
     * @param workflowLazyQuery
     * @param lang
     * @param dateFormat
     * @param timeFormat
     * @return
     */
    private void exportWorkflowsToCsv(WorkflowLazyQuery workflowLazyQuery, OutputStream outStream, String lang, String dateFormat, String timeFormat, String timeZone, boolean withAttachments, String csvFileName) {
        CSVFormat csvFileFormat = CSVFormat.EXCEL;
        List<String> csvHeader = new ArrayList<>();

        // Defining header for Standard Fields (sf)
        Locale locale = new Locale(lang);
        csvHeader.add("ID");
        csvHeader.add(LabelUtils.getLabel("general.docs.number", locale));
        csvHeader.add(LabelUtils.getLabel("general.workflow.state", locale));
        csvHeader.add(LabelUtils.getLabel("general.docs.title", locale));
        csvHeader.add(LabelUtils.getLabel("general.docs.subject", locale));
        csvHeader.add(LabelUtils.getLabel("general.docs.author", locale));
        csvHeader.add(LabelUtils.getLabel("general.docs.dateCreated", locale));
        csvHeader.add(LabelUtils.getLabel("general.docs.dateIncome", locale));
        csvHeader.add(LabelUtils.getLabel("general.docs.dateDue", locale));
        csvHeader.add(LabelUtils.getLabel("general.docs.description", locale));

        List<String> csvCfHeaderNames = new ArrayList<>(); // List of custom field names according current lang

        File file;
        try {
            file = File.createTempFile("export", ".csv", new File(TMP_DIR));
        } catch (IOException ex) {
            LOG.error("Error creating temporary file for Workflow export", ex);
            return;
        }

        try (OutputStreamWriter out = new FileWriter(file, StandardCharsets.UTF_8); CSVPrinter csvPrinter = new CSVPrinter(out, csvFileFormat);) {
            workflowLazyQuery.setPageSize(EXPORT_MAX_DOCS);
            workflowLazyQuery.setFirst(-1);

            BasicModelProcessor<Workflow> workflowProcessor = new BasicModelProcessor<Workflow>() {
                @Override
                public void process(Workflow workflow) {
                    try {
                        for (List<String> record : exportWorkflow(workflow, csvCfHeaderNames, outStream, lang, dateFormat, timeFormat, timeZone, withAttachments)) {
                            csvPrinter.printRecord(record);
                        }
                        out.flush();
                    } catch (IOException ex) {
                        LOG.error(null, ex);
                    }
                }
            };

            _workflowDao.processWithCursor(workflowLazyQuery, workflowProcessor, SecurityUtils.getSubjectAclContext());

            // Adding custom field header to CSV header
            csvCfHeaderNames.forEach(csvHeader::add);

            if (outStream instanceof ZipOutputStream) {
                appendCsvHeaderToCsvContentAndWriteToZipOutput(file, csvHeader, csvFileFormat, (ZipOutputStream) outStream, csvFileName);
            } else {
                appendCsvHeaderToCsvContent(file, csvHeader, csvFileFormat, outStream);
            }
        } catch (Exception ex) {
            LOG.error("CsvExport.export()", ex);
        }
    }


    /**
     * Processes provided {@link Workflow} and generates list of String rows to be used for CSV export
     *
     * @param workflow
     * @param csvCfHeaderNames
     * @param langSysCode
     * @param dateFormat
     * @param timeFormat
     * @return
     */
    private List<List<String>> exportWorkflow(Workflow workflow, List<String> csvCfHeaderNames, OutputStream outStream, String langSysCode, String dateFormat, String timeFormat, String timeZone, boolean withAttachments) {
        List<String> wfBaseRecord = new ArrayList<>();
        List<List<String>> wfRecords = new ArrayList<>(); // List of all records, generated by doc form
        wfBaseRecord.add(workflow.getId());
        wfBaseRecord.add(workflow.getNum() != null ? workflow.getNum() : "");

        // Calculates Workflow State Name
        String stateName = WorkflowStateConverter.getLabelByState(workflow.getState(), workflow, I18n.getLocaleByLangSysCode(langSysCode));
        wfBaseRecord.add(stateName);

        wfBaseRecord.add(_formFieldsUtils.getFieldData(workflow.getNameByLang(langSysCode), DataType.STRING, dateFormat, timeFormat, timeZone));
        wfBaseRecord.add(_formFieldsUtils.getFieldData(workflow.getTopic(), DataType.STRING, dateFormat, timeFormat, timeZone));

        // Calculate Author UI name
        String systemId = null;
        try {
            systemId = workflow.getAuthor();
            wfBaseRecord.add(_directoryService.getDirectoryItem(systemId).getItemNameUI());
        } catch (Exception e) {
            LOG.error("Workflow[{}]: Error calculating Workflow Author for author systemId:{}", workflow.getId(), systemId, e);
            wfBaseRecord.add(FIELD_ERROR_VALUE);
        }

        wfBaseRecord.add(_formFieldsUtils.getFieldData(workflow.getDateCreated(), DataType.DATE, dateFormat, timeFormat, timeZone));
        wfBaseRecord.add(_formFieldsUtils.getFieldData(workflow.getDateReceived(), DataType.DATE, dateFormat, timeFormat, timeZone));
        wfBaseRecord.add(_formFieldsUtils.getFieldData(workflow.getDateDue(), DataType.DATE, dateFormat, timeFormat, timeZone));

        // Extract document description and clean it from HTML tags, if needed
        String desc = _formFieldsUtils.getFieldData(workflow.getNote(), DataType.STRING, dateFormat, timeFormat, timeZone);
        if (workflow.getSfDef().getDescription().isUseRTeditor()) {
            wfBaseRecord.add(FormFieldsUtils.htmlToPlainText(desc));
        } else {
            wfBaseRecord.add(desc);
        }

        // List of all records, generated by doc form
        List<Map<String, String>> workflowCfValues = processDoc(workflow, csvCfHeaderNames, new ArrayList<>(Arrays.asList(new HashMap<>())), outStream, langSysCode, dateFormat, timeFormat, timeZone, withAttachments);

        // Process recognition documents
        if (workflow.isRecoWF()) {
            // Add RecoData-related columns
            if (!csvCfHeaderNames.contains("RecoForm")) {
                csvCfHeaderNames.add("RecoForm");
                csvCfHeaderNames.add("Start page");
                csvCfHeaderNames.add("End page");
                csvCfHeaderNames.add("RecoData status");
            }

            // Create global list of rows. Required, as we replace existing rows with rows with RecoData added
            List<Map<String, String>> recoDocRows = new ArrayList<>();
            List<Map<String, String>> baseRows = new ArrayList<>(workflowCfValues);

            try {
                List<RecoData> recoDataList = _recoWorkflowService.getRecoDataList(workflow);
                recoDataList.forEach(recoData -> {
                    baseRows.forEach(row -> {
                        row.put("RecoForm", recoData.getRecoForm().getName());
                        row.put("Start page", String.valueOf(recoData.getPageIdxStart() + 1));
                        row.put("End page", String.valueOf(recoData.getPageIdxEnd() + 1));
                        row.put("RecoData status", recoData.isValidated() ? "Validated" : "Requires validation");
                    });
                    List<Map<String, String>> addedRows = processDoc(recoData, csvCfHeaderNames, baseRows, outStream, langSysCode, dateFormat, timeFormat, timeZone, withAttachments);
                    recoDocRows.addAll(addedRows);
                });
                workflowCfValues.clear();
                workflowCfValues.addAll(recoDocRows);
            } catch (Exception ex) {
                LOG.error("Workflow[{}]: Error processing RecoData", workflow.getId(), ex);
            }
        }

        // Fill CSV record slots with values from the current document, empty slots to be filled with empty strings
        workflowCfValues.forEach(record -> {
            List<String> csvRecord = new ArrayList<>(wfBaseRecord);
            csvCfHeaderNames.forEach(fieldName -> {
                // Current document may not have field for current slot
                if (record.containsKey(fieldName)) {
                    csvRecord.add(record.get(fieldName));
                } else {
                    // fill the empty slot
                    csvRecord.add("");
                }
            });
            wfRecords.add(csvRecord);
        });

        if (wfRecords.isEmpty()) {
            wfRecords.add(wfBaseRecord);
        }

        return wfRecords;
    }


    /**
     * Processes CustomFieldAwareModel by exporting its values from Custom Fields
     *
     * @param cfAwareModel
     * @param csvCfHeaderNames
     * @param baseRows
     * @param lang
     * @param dateFormat
     * @param timeFormat
     * @return
     */
    private List<Map<String, String>> processDoc(CustomFieldAwareModel cfAwareModel, List<String> csvCfHeaderNames, List<Map<String, String>> baseRows, OutputStream outStream, String lang, String dateFormat, String timeFormat, String timeZone, boolean withAttachments) {
        List<Map<String, String>> documentRows = new ArrayList<>();
        List<Map<String, String>> processedRows = new ArrayList<>();
        baseRows.stream().map(HashMap::new).forEach(processedRows::add);

        // Process Custom Fields except tables, OMR and signs
        cfAwareModel.getCfDef().stream()
                .filter(cf -> !CustomField.TYPE_TABLE.equals(cf.getType()) && !CustomField.TYPE_RECO_TABLE.equals(cf.getType())) // Skip all table fields, as they will be processed later
                .filter(cf -> !CustomField.TYPE_RECO_OMR.equals(cf.getType())) // Skip OMR fields, as they will be processed later
                .filter(cf -> !CustomField.TYPE_RECO_IMAGE.equals(cf.getType())) // Skip IMAGE fields, as they will be processed later
                .filter(cf -> !CustomField.TYPE_SIGN.equals(cf.getType())) // Skip fields of Signature type
                .filter(CustomField::isEnabled) // iterate over Custom Fields only in case Custom Field is Enabled on the document
                .forEach(customField -> processBaseField(cfAwareModel, customField, csvCfHeaderNames, processedRows, lang, dateFormat, timeFormat, timeZone));

        // Process Custom Fields of OMR type. At this moment, workflowBaseRecord should contain all non-OMR values
        cfAwareModel.getCfDef().stream()
                .filter(cf -> CustomField.TYPE_RECO_OMR.equals(cf.getType())) // Skip all non-table fields, as they was already processed
                .filter(CustomField::isEnabled) // iterate over Custom Fields only in case Custom Field is Enabled on the document
                .forEach(customField -> processOmrField(cfAwareModel, customField, csvCfHeaderNames, processedRows, lang));

        // Process Custom Fields of IMAGE type. At this moment, workflowBaseRecord should contain all non-IMAGE values
        cfAwareModel.getCfDef().stream()
                .filter(cf -> CustomField.TYPE_RECO_IMAGE.equals(cf.getType())) // Skip all non-image fields, as they was already processed
                .filter(CustomField::isEnabled) // iterate over Custom Fields only in case Custom Field is Enabled on the document
                .forEach(customField -> processImageField(cfAwareModel, customField, csvCfHeaderNames, processedRows, outStream, lang, withAttachments));

        // Process Custom Fields of table types. At this moment, workflowBaseRecord should contain all non-table values
        cfAwareModel.getCfDef().stream()
                .filter(cf -> CustomField.TYPE_TABLE.equals(cf.getType()) || CustomField.TYPE_RECO_TABLE.equals(cf.getType())) // Skip all non-table fields, as they was already processed
                .filter(CustomField::isEnabled) // iterate over Custom Fields only in case Custom Field is Enabled on the document
                .forEach(customField -> processTableField(cfAwareModel, customField, csvCfHeaderNames, processedRows, documentRows, outStream, lang, withAttachments));

        // If workflowRecords is empty on this step - no tables was processed and we need to add base record to the list to be stored
        if (documentRows.isEmpty()) {
            documentRows.addAll(processedRows);
        }
        return documentRows;
    }


    /**
     * Adds value of the Base CustomField (non-table, OMR, image, sign) to export CSV
     *
     * @param cfAwareModel
     * @param customField
     * @param csvCfHeaderNames
     * @param processedRows
     * @param lang
     * @param dateFormat
     * @param timeFormat
     * @param timeZone
     */
    protected void processBaseField(
            CustomFieldAwareModel cfAwareModel,
            CustomField customField,
            List<String> csvCfHeaderNames,
            List<Map<String, String>> processedRows,
            String lang,
            String dateFormat,
            String timeFormat,
            String timeZone) {
        String fieldId = cfAwareModel.getCfDsFieldName(customField);
        String fieldName = customField.getName().get(lang);

        // In case global csv header list (map) doesn't contain the current fieldName - we need to add it
        // in order to have the complete list of columns
        if (!csvCfHeaderNames.contains(fieldName)) {
            csvCfHeaderNames.add(fieldName);
        }

        processedRows.forEach(documentRow -> {
            try {
                // IMPORTANT: Custom field of Date type must use UTC timezone to avoid date part shift due to timezone offset
                String cfTimeZone = CustomField.TYPE_DATE.equals(customField.getType()) ? "UTC" : timeZone;
                documentRow.put(fieldName, _formFieldsUtils.getCustomFieldData(cfAwareModel, fieldId, customField.getType(), lang, dateFormat, timeFormat, cfTimeZone));
            } catch (Exception ex) {
                documentRow.put(fieldName, FIELD_ERROR_VALUE);
            }
        });
    }


    protected void processOmrField(
            CustomFieldAwareModel cfAwareModel,
            CustomField customField,
            List<String> csvCfHeaderNames,
            List<Map<String, String>> processedRows,
            String lang
    ) {
        String omrId = cfAwareModel.getCfDsFieldName(customField);
        String omrName = customField.getName().get(lang);

        // In case global csv header list (map) doesn't contain the current table_column - transfer column names to CSV
        customField.getFields().stream()
                .map(field -> field.getName().get(lang))
                .map(markName -> omrName + " | " + markName)
                .filter(exportMarkName -> (!csvCfHeaderNames.contains(exportMarkName)))
                .forEachOrdered(csvCfHeaderNames::add);

        List<Boolean> omrData = (List<Boolean>) cfAwareModel.getCfData().get(omrId);

        for (int i = 0; i < customField.getFields().size(); i++) {
            CustomField mark = customField.getFields().get(i);
            String exportMarkName = omrName + " | " + mark.getName().get(lang);
            String markValue;
            if (omrData != null && i < omrData.size()) {
                markValue = String.valueOf(omrData.get(i));
            } else {
                markValue = FIELD_ERROR_VALUE;
            }

            processedRows.forEach(documentRow -> {
                documentRow.put(exportMarkName, String.valueOf(markValue));
            });
        }
    }


    protected void processTableField(
            CustomFieldAwareModel cfAwareModel,
            CustomField customField,
            List<String> csvCfHeaderNames,
            List<Map<String, String>> processedRows,
            List<Map<String, String>> documentRows,
            OutputStream zos,
            String lang,
            boolean withAttachments) {
        String tableId = cfAwareModel.getCfDsFieldName(customField);
        String tableName = customField.getName().get(lang);

        // In case global csv header list (map) doesn't contain the current table_column - transfer column names to CSV
        customField.getFields().stream()
                .filter(field -> !field.getRecoProp().isIgnored()) // Do not process ignored columns
                .map(field -> field.getName().get(lang))
                .map(columnName -> tableName + " | " + columnName)
                .filter(exportColumnName -> (!csvCfHeaderNames.contains(exportColumnName)))
                .forEachOrdered(csvCfHeaderNames::add);

        List<List<String>> tableData = ObjectValueUtils.getValueAsListOfStringList(cfAwareModel.getCfData().get(tableId));

        processedRows.forEach(recoDataRow -> {
            tableData.forEach(row -> {
                // Copy already processed field values to new row
                Map<String, String> csvRowValues = new HashMap<>(recoDataRow);

                // Process table row
                for (int k = 0; k < customField.getFields().size(); k++) {
                    CustomField column = customField.getFields().get(k);
                    if (column.getRecoProp().isIgnored()) {
                        continue;
                    }
                    String columnName = column.getName().get(lang);
                    String exportColumnName = tableName + " | " + columnName;
                    String fieldValue;
                    if (row.size() > k) {
                        if (CustomField.TYPE_RECO_IMAGE.equals(column.getType())) {
                            if (isNotBlank(row.get(k))) {
                                if (withAttachments) {
                                    try {
                                        fieldValue = "Files/" + row.get(k) + "." + DEFAULT_IMAGE_FIELD_FORMAT;
                                        addRecoDataFileToZip((ZipOutputStream) zos, row.get(k)); // If withAttachments = true - we working with zip, so it is safe to cast
                                    } catch (Exception ex) {
                                        fieldValue = FIELD_ERROR_VALUE;
                                        LOG.error("Error adding table cell [{}][{}] image to export archive", tableName, columnName, ex);
                                    }
                                } else {
                                    fieldValue = row.get(k);
                                }
                            } else {
                                fieldValue = "";
                            }
                        } else {
                            fieldValue = row.get(k);
                        }
                    } else {
                        // Table row doesn't have value for the requested column
                        fieldValue = FIELD_ERROR_VALUE;
                    }
                    csvRowValues.put(exportColumnName, fieldValue);
                }

                // Add table row to list of CSV rows, generated by Workflow
                documentRows.add(csvRowValues);
            });
        });
    }


    /**
     * Processes image field by extracting image from the DB and writing it to the ZipOutputStream
     *
     * @param cfAwareModel
     * @param customField
     * @param csvCfHeaderNames
     * @param processedRows
     * @param zos
     * @param lang
     * @param withAttachments
     */
    protected void processImageField(
            CustomFieldAwareModel cfAwareModel,
            CustomField customField,
            List<String> csvCfHeaderNames,
            List<Map<String, String>> processedRows,
            OutputStream zos,
            String lang,
            boolean withAttachments) {
        String fieldName = customField.getName().get(lang);
        String fieldAttachmentId = (String) cfAwareModel.getCfData().get(cfAwareModel.getCfDsFieldName(customField));

        String fieldValue;

        if (withAttachments) {
            try {
                if (isNotBlank(fieldAttachmentId)) {
                    fieldValue = "Files/" + fieldAttachmentId + ".png";
                    addRecoDataFileToZip((ZipOutputStream) zos, fieldAttachmentId); // If withAttachments = true - we working with zip, so it is safe to cast
                } else {
                    fieldValue = "";
                }
            } catch (Exception ex) {
                fieldValue = FIELD_ERROR_VALUE;
                LOG.error("Error adding field[{}] image to export archive", fieldName, ex);
            }
        } else {
            fieldValue = fieldAttachmentId;
        }

        // In case global csv header list (map) doesn't contain the current fieldName - we need to add it
        // in order to have the complete list of columns
        if (!csvCfHeaderNames.contains(fieldName)) {
            csvCfHeaderNames.add(fieldName);
        }

        for (Map<String, String> documentRow : processedRows) {
            documentRow.put(fieldName, fieldValue);
        }
    }


    /**
     * Gets File from database (RecoData attachment) by its ID and adds it to ZIP file.<br>
     * <b>IMPORTANT:</b> assumes, that requested attachment is IMAGE-type field value
     *
     * @param zos
     * @param fieldAttachmentId
     */
    private void addRecoDataFileToZip(ZipOutputStream zos, String fieldAttachmentId) {
        if (isBlank(fieldAttachmentId)) {
            return;
        }
        DbFileRef attachmentRef = _recoDataDao.getFilesDao().createDbFileRef(fieldAttachmentId, new ArrayList<>());
        try {
            ZipEntry newZipEntry = new ZipEntry("Files/" + fieldAttachmentId + "." + DEFAULT_IMAGE_FIELD_FORMAT);
            zos.putNextEntry(newZipEntry);
            _recoDataDao.getFilesDao().downloadToStream(attachmentRef, zos);
            zos.closeEntry();
        } catch (IOException ex) {
            throw new ElDocRuntimeException("Error loading attachment file", ex);
        }
    }


    /**
     * Updates and saves CSV file to ZipOutputStream, performing following steps:<br>
     * <ul>
     * <li>Appends provided CSV header line to the new output;</li>
     * <li>For each row in initial file content, if it has less columns, than defined by header - adds empty values in the end of the row;</li>
     * <li>Writes each row (adjusted on previous steps) to the new output;</li>
     * </ul>
     *
     * @param csvExportContentFile
     * @param header
     * @param csvFileFormat
     * @param zos
     * @param entryName
     * @throws IOException
     */
    private void appendCsvHeaderToCsvContentAndWriteToZipOutput(File csvExportContentFile, List<String> header, CSVFormat csvFileFormat, ZipOutputStream zos, String entryName) throws IOException {
        ZipEntry zipEntry = new ZipEntry(entryName);
        zos.putNextEntry(zipEntry);
        appendCsvHeaderToCsvContent(csvExportContentFile, header, csvFileFormat, zos);
        zos.closeEntry();
    }


    public void appendCsvHeaderToCsvContent(File csvExportContentFile, List<String> header, CSVFormat csvFileFormat, OutputStream os) {
        try {
            OutputStreamWriter out = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            out.write('\ufeff'); // Adding BOM marker for simple opening in Excel
            CSVPrinter csvPrinter = new CSVPrinter(out, csvFileFormat);
            csvPrinter.printRecord(header);

            CSVParser.parse(csvExportContentFile, StandardCharsets.UTF_8, csvFileFormat).stream()
                    .map(CSVRecord::toList)
                    .peek(row -> row.addAll(Collections.nCopies(header.size() - row.size(), ""))) // If current row contains less cells, than defined by header - add empty cells
                    .forEach(row -> {
                        try {
                            csvPrinter.printRecord(row);
                        } catch (IOException ex) {
                            LOG.error(null, ex);
                        }
                    });

            out.flush();
            os.flush();
        } catch (IOException ex) {
            LOG.error(null, ex);
        } finally {
            _filesCleanupService.addQueueItem(csvExportContentFile.toPath());
        }
    }
}
