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
package ai.eldoc.reco;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.TenantContext;
import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.ai.service.AiService;
import ai.eldoc.dao.DaoUtils;
import ai.eldoc.exceptions.RecoWorkflowRecognitionException;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.*;
import ai.eldoc.ocr.OcrSymbol;
import ai.eldoc.ocr.OcrText;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.service.RecoFormService;
import ai.eldoc.util.I18n;
import ai.eldoc.util.RectangleUtils;
import jakarta.json.*;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;
import static ai.eldoc.model.PropertyConfig.IDP_AI_MAX_PAGES_PER_REQUEST;
import static java.text.MessageFormat.format;
import static java.util.function.Predicate.not;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Performs matching of RecoForm with the provided document, using AI (LLM)
 */
public class RecoFormAiProcessor extends AbstractRecoFormProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(RecoFormAiProcessor.class);

    private List<RecoField> _fieldsToBeMatched;
    private RecoPage _recoPage;
    private int _recoPageIdx;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoFormAiProcessor(Workflow recoWF, RecognitionMetaDoc recoMetaDoc, RecognitionMetaFragment recoMetaFragment, DbFile recoFile, RecoForm recoForm, AiRequestContext billingRequestContext) {
        super(recoWF, recoMetaDoc, recoMetaFragment, recoFile, recoForm, billingRequestContext);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    void initRecoFormProcessor() {
        // Check whether preprocessed image is in place
        DbFile preprocessedFile = DaoUtils.getWorkflowDaoTenant().getFilesDao().getFile(null, getRecoWF().getFileByTag(Workflow.FILE_TAG.IDP_PREPROCESSED.name()));
        if (preprocessedFile == null) {
            String msg = format("Can not perform matching with RecoForm[{0}] as source file was not located", getRecoForm().getName());
            throw new RecoWorkflowRecognitionException(msg);
        }

        getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_FORM, "AI form found, extracting all available data");
        try (DbFileDownloadStream downloadStream = DaoUtils.getWorkflowDaoTenant().getFilesDao().getFileDownloadStream(preprocessedFile.getDbFileRef())) {
            setRecoFormResultImported(processDocument(preprocessedFile.getDbFileRef(), downloadStream));
        } catch (IOException ex) {
            throw new ElDocRuntimeException("Error during document processing", ex);
        }
    }


    @Override
    void processWithTemplateInner() {
        if (getRecoFormResultImported() != null) {
            // Document and LLM result was already initialized
        } else {
            // Check whether preprocessed image is in place
            DbFile sourceFile = DaoUtils.getWorkflowDaoTenant().getFilesDao().getFile(null, getRecoWF().getFileByTag(Workflow.FILE_TAG.IDP_PREPROCESSED.name()));
            if (sourceFile == null) {
                String msg = format("Can not perform matching with RecoForm[{0}] as source file was not located", getRecoForm().getName());
                throw new RecoWorkflowRecognitionException(msg);
            }
            try (DbFileDownloadStream downloadStream = DaoUtils.getWorkflowDaoTenant().getFilesDao().getFileDownloadStream(sourceFile.getDbFileRef())) {
                RecoFormResult recoFormResult = processDocument(sourceFile.getDbFileRef(), downloadStream);
                setRecoFormResultImported(recoFormResult);
            } catch (IOException ex) {
                LOG.error("RecoWorkflow[{}] & RecoForm[{}] error reading document source file", getRecoWF().getId(), getRecoForm().getId(), ex);
                throw new RecoWorkflowRecognitionException(format("RecoWorkflow[{}] & RecoForm[{1}] error reading document source file", getRecoWF().getId(), getRecoForm().getId()));
            }
        }

        if (getRecoFormResultImported() == null) {
            throw new RecoWorkflowRecognitionException((format("RecoWorkflow[{}] & RecoForm[{1}] error obtaining processed data from AI", getRecoWF().getId(), getRecoForm().getId())));
        }

        initRecoFormResult();

        getRecoFormResult().getExtraFiles().addAll(getRecoFormResultImported().getExtraFiles());

        int docPageIdx = 0;

        for (_recoPageIdx = getRecoForm().getFirstPageWithFields(); _recoPageIdx <= getRecoForm().getLastPageWithFields(); _recoPageIdx++) {
            if (Thread.interrupted()) {
                LOG.debug("RecoWorkflow[{}] | RecoForm[{}]({}) matching was interrupted - exiting", getRecoWF().getId(), getRecoForm().getId(), getRecoForm().getName());
                Thread.currentThread().interrupt();
                return;
            }
            // Prepare template page
            _recoPage = getRecoForm().getPages().get(_recoPageIdx);

            // Get the list of all fields to be located
            _fieldsToBeMatched = new ArrayList<>(getRecoForm().getFieldsArrangedByValueRect(_recoPage));

            if (_fieldsToBeMatched.isEmpty()) {
                // Currently processed RecoPage doesn't have fields defined - skip processing...
                if (_recoPage.getProp().isApplyPageBreakBefore()) {
                    docPageIdx++;
                }
                continue;
            }

            if (_recoPageIdx != getRecoForm().getFirstPageWithFields() && (_recoPage.getProp().isApplyPageBreakBefore() || getRecoForm().isFloatingFieldCheckAndGetExtendableField(_fieldsToBeMatched.getFirst()) == null)) {
                // Field is marked to start a new page, even if it is supposed to be floating
                docPageIdx++;
            }

            int docPageStartIdx = getRecoMetaDoc().getCurrentPageIdx() + docPageIdx;

            if (docPageStartIdx > getRecoMetaFragment().getPageEnd()) {
                // End of the document fragment reached, but there is still RecoFields left on the form
                RecoPageResult recoPageResult = getRecoPageResultForPageIdx(docPageStartIdx);
                if (recoPageResult == null) {
                    // RecoFormResult doesn't contain results for current page - create new RecoPageResult
                    recoPageResult = new RecoPageResult(docPageStartIdx);
                    getRecoFormResult().getPages().add(recoPageResult);
                }
                for (RecoField recoField : _fieldsToBeMatched) {
                    RecoFieldResult fieldResult = RecoFieldResult.createNotFound(getRecoForm(), recoField);
                    recoPageResult.getFields().add(fieldResult);
                }
            }

            // Start processing document pages one by one
            docPageIdx = matchFields(docPageIdx, getRecoMetaDoc().getNumberOfPages(), _fieldsToBeMatched);

            // We need to 'skip' pages in 'extra' pages (see RecoForm.getLastRelatedPageIndex Javadoc)
            _recoPageIdx = getRecoForm().getLastRelatedPageIndex(_recoPage);
        }
    }


    private RecoFormResult processDocument(DbFileRef sourceFileRef, InputStream downloadStream) throws IOException {
        try (PDDocument doc = PdfUtils.loadPDF(downloadStream)) {
            return processDocument(sourceFileRef, doc);
        }
    }


    private RecoFormResult processDocument(DbFileRef sourceFileRef, PDDocument doc) throws IOException {
        AiRequestContext aiContext;
        if (getBillingRequestContext() instanceof AiRequestContext aiRequestContext) {
            aiContext = aiRequestContext;
        } else {
            aiContext = AiRequestContext.forSrcIDP(I18n.LANG_SYSTEM_CODE_EN, TenantContext.getCurrentTenantId(), getBillingRequestContext().getAclContext());
        }

        String formFieldsSchema = getRecoFormSchema();
        if (isBlank(formFieldsSchema) && !RecoForm.AUTOMATIC_FORM_ID.equals(getRecoForm().getId())) {
            // This is not auto-form, but it has no fields - skip processing
            return createRecoFormResult(1);
        }

        AiService chatService = AiService.getInstance();
        int pageStart = getRecoMetaFragment().getPageStart();
        int pageEnd = getRecoMetaFragment().getPageEnd() + 1;
        int pagesPerRequest = getRecoForm().getProperty(IDP_AI_MAX_PAGES_PER_REQUEST, -1);
        Map<String, Object> fieldValues = chatService.extractStructuredDataFromImage(getRecoForm().getExtractionModel(), formFieldsSchema, sourceFileRef, doc, pageStart, pageEnd, pagesPerRequest, aiContext);
        if (fieldValues == null) {
            // AI was not able to extract requested values
            return createRecoFormResult(1);
        }
        RecoFormResult formResult = valuesToRecoFormResult(doc, fieldValues);

        if (RecoForm.AUTOMATIC_FORM_ID.equals(getRecoForm().getId())) {
            // This is not auto-form, but it has no fields - skip processing
            RecoFormService.recoFormResultToRecoForm(formResult, getRecoForm());
        }

        return formResult;
    }


    private RecoFormResult valuesToRecoFormResult(PDDocument doc, Map<String, Object> values) throws IOException {
        return valuesToRecoFormResult(doc, getRecoForm(), values, getRecoMetaFragment().getPageStart(), getRecoMetaFragment().getPageEnd() + 1);
    }


    private RecoFormResult valuesToRecoFormResult(PDDocument doc, RecoForm recoForm, Map<String, Object> values, int pageStart, int pageEnd) throws IOException {
        List<OcrText> pagesText = new ArrayList<>();
        for (int pageIdx = pageStart; pageIdx < pageEnd; pageIdx++) {
            pagesText.add(PdfUtils.getTextWithMeta(doc, pageIdx));
        }

        RecoFormResult result = createRecoFormResult(pageEnd - pageStart);

        for (Entry<String, Object> fieldContent : values.entrySet()) {
            if (fieldContent.getValue() == null || isBlank(String.valueOf(fieldContent.getValue()))) {
                // AI returned "null" as field value, while should've skipped the field - do not treat as error, but treat result as "not found"
                continue;
            }
            String fieldName = fieldContent.getKey();
            RecoField recoField = recoForm.getFields().stream()
                    .filter(field -> Objects.equals(field.getName(), fieldName))
                    .findFirst().orElse(null);
            boolean isTableFieldResult = isTableFieldValue(fieldContent.getValue());
            List<Pair<RecoFieldResult, Integer>> parsedResult;
            if (isTableFieldResult) {
                parsedResult = aiResultToTableFieldResult(fieldName, fieldContent.getValue(), recoField, pagesText);
            } else if (recoField == null || recoField.isTypeText()) {
                Triple<String, Integer, Integer> fieldTextAiValueWithPageIdx = parseFieldValue(fieldContent.getValue());
                if (isBlank(fieldTextAiValueWithPageIdx.getLeft())) {
                    // AI returned "null" as field value, while should've skipped the field - do not treat as error, but treat result as "not found"
                    continue;
                }
                Pair<RecoFieldResult, Integer> textFieldResult = aiResultToTextFieldResult(fieldName, fieldTextAiValueWithPageIdx.getLeft(), fieldTextAiValueWithPageIdx.getMiddle(), recoField, pagesText, fieldTextAiValueWithPageIdx.getRight());
                parsedResult = List.of(textFieldResult);
            } else {
                continue;
            }
            for (Pair<RecoFieldResult, Integer> fieldResult : parsedResult) {
                int pageIdx = fieldResult.getRight();
                pageIdx = pageIdx == pagesText.size() ? 0 : pageIdx; // Adjust page IDX if it was not actually found (outside of list of available pages)
                result.getPages().get(pageIdx).getFields().add(fieldResult.getLeft());
            }
        }

        return result;
    }


    private Pair<RecoFieldResult, Integer> aiResultToTextFieldResult(String fieldName, String fieldTextAiValue, int valueConf, RecoField recoField, List<OcrText> pagesText, int pageIdx) {
        Pair<OcrText, Integer> fieldCoords;
        if (pageIdx == -1) {
            fieldCoords = findTextCoords(pagesText, fieldTextAiValue, List.of());
        } else {
            int pageIdxToUse = Math.clamp(pageIdx, 0, pagesText.size() - 1);
            OcrText value = findTextCoords(pagesText.get(pageIdxToUse), fieldTextAiValue, List.of());
            fieldCoords = Pair.of(value, pageIdxToUse);
        }
        RecoFieldResult fieldResult = RecoFieldResult.createFormless();
        fieldResult.setName(fieldName);
        fieldResult.setType(RecoFieldDataType.TEXT);
        fieldResult.setMatchConfidence(100);
        fieldResult.setValue(normalizeAiTextValue(fieldTextAiValue));

        if (fieldCoords.getLeft() == null) {
            // Field value is not found on any page - use confidence threshold -1 as value confidence to send field for validation
            int charConf = valueConf == -1
                    ? recoField == null
                      ? RecoField.CONF_THRESH_DEFAULT
                      : recoField.getConfidenceThreshold() + 1
                    : valueConf;
            if (recoField != null && isNotBlank(recoField.getTextProp().getDateTimeFormat())) {
                // Field has DateTime format specified - there's almost 100% chance that value was not found due to AI Agent applying formatting to it
                // Do not send field for validation in this case
                charConf = Math.min(recoField.getConfidenceThreshold() + 1, 100);
            }
            fieldResult.getValueConf().addAll(Collections.nCopies(fieldTextAiValue.length(), charConf));
            fieldResult.setRegion(new Rectangle());
        } else {
            // Value, extracted by AI, exactly matches text fragment from page
            fieldCoords.getLeft().getOcrData().stream()
                    .map(OcrSymbol::getConfidence)
                    .forEach(fieldResult.getValueConf()::add);
            fieldResult.setRegion(fieldCoords.getLeft().getTextArea());
        }

        return Pair.of(fieldResult, fieldCoords.getRight());
    }


    private Triple<String, Integer, Integer> parseFieldValue(Object fieldContent) {
        switch (fieldContent) {
            case null -> {
                return Triple.of("", -1, -1);
            }
            case String str -> {
                return Triple.of(str, -1, -1);
            }
            case Number number -> {
                return Triple.of(String.valueOf(number), -1, -1);
            }
            case Boolean bool -> {
                return Triple.of(String.valueOf(bool), -1, -1);
            }
            case Map<?, ?> map -> {
                String fieldValue = parseValue(map);
                Integer pageNumber = parsePageNumber(map);
                Integer confidence = parseConfidence(map);
                return Triple.of(fieldValue, confidence, pageNumber);
            }
            default -> {
            }
        }

        return Triple.of(String.valueOf(fieldContent), -1, -1);
    }


    /**
     * Tries to parse page number from AI response.
     *
     * @param map Response object from AI
     * @return 0-based page number, or -1 if the page number is not found in the response
     */
    private Integer parsePageNumber(Map<?, ?> map) {
        Object rawPage = firstPresent(
                map,
                "p", "page", "pageNumber", "pageIdx", "pageIndex", "page_number"
        );

        Integer pageNumber = parseInteger(rawPage);
        if (pageNumber == null || pageNumber < 1) {
            return -1;
        }
        return pageNumber - 1; // Model returns 1-based page number, while we use 0-based
    }


    /**
     * Tries to parse value from AI response.
     *
     * @param map Response object from AI
     * @return value, or null if the value is not found in the response
     */
    private String parseValue(Map<?, ?> map) {
        Object rawValue = firstPresent(
                map,
                "v", "val", "value"
        );

        if (rawValue == null) {
            return null;
        }
        return String.valueOf(rawValue);
    }


    /**
     * Tries to parse confidence level from AI response.
     *
     * @param map Response object from AI
     * @return confidence level, or -1 if the confidence level is not found in the response
     */
    private Integer parseConfidence(Map<?, ?> map) {
        Object rawConfidence = firstPresent(
                map,
                "c", "conf", "confidence"
        );

        Integer confValue = parseInteger(rawConfidence);
        if (confValue == null || confValue < 1) {
            return -1;
        }
        return confValue;
    }


    private Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }


    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Integer integer) {
            return integer;
        }

        if (value instanceof Number number) {
            int intValue = number.intValue();

            // Avoid silently accepting 2.5 as 2
            if (number.doubleValue() == intValue) {
                return intValue;
            }

            return null;
        }

        if (value instanceof String str) {
            String normalized = str.trim();

            if (normalized.isEmpty()) {
                return null;
            }

            try {
                return Integer.parseInt(normalized);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }


    private static String normalizeAiTextValue(String value) {
        if (isBlank(value) || Strings.CI.equals("null", value)) {
            return null;
        }

        return value.trim();
    }


    private List<Pair<RecoFieldResult, Integer>> aiResultToTableFieldResult(String fieldName, Object fieldAiValue, RecoField recoField, List<OcrText> pagesText) {
        List<Map<String, Object>> tableData;
        if (fieldAiValue instanceof List listValue && isTableFieldValue(fieldAiValue)) {
            tableData = listValue;
        } else {
            // We expect value to be already parsed before this method is called; therefore, anything except List is wrong value
            return List.of(Pair.of(null, -1));
        }

        Map<Integer, List<List<RecoFieldResult>>> parsedTableData = new HashMap<>();
        List<OcrText> alreadyUsedTextCache = new ArrayList<>();
        for (Map<String, Object> tableRow : tableData) {
            List<RecoFieldResult> rowResults = new ArrayList<>();
            int pageIdx = -1;
            for (Map.Entry<String, Object> aiCellResult : tableRow.entrySet()) {
                RecoField column = recoField == null
                        ? null
                        : recoField.getColumns().stream()
                          .filter(col -> Objects.equals(col.getName(), aiCellResult.getKey()))
                          .findFirst().orElse(null);
                if (column != null) {
                    column.setAnchorText(aiCellResult.getKey());
                }
                int charConf = column == null ? RecoField.CONF_THRESH_DEFAULT : column.getConfidenceThreshold();
                Triple<String, Integer, Integer> cellTextAiValueWithPageIdx = parseFieldValue(aiCellResult.getValue());
                String fieldTextAiValue = String.valueOf(cellTextAiValueWithPageIdx.getLeft() != null ? cellTextAiValueWithPageIdx.getLeft() : "");
                pageIdx = Math.clamp(cellTextAiValueWithPageIdx.getRight(), 0, pagesText.size() - 1);
                RecoFieldResult cellResult = RecoFieldResult.createFormless();
                cellResult.setName(aiCellResult.getKey());
                cellResult.setMatchConfidence(100);
                cellResult.setValue(normalizeAiTextValue(fieldTextAiValue));
                if (cellTextAiValueWithPageIdx.getMiddle() != -1) {
                    cellResult.setValueConf(List.of(cellTextAiValueWithPageIdx.getMiddle()));
                } else {
                    cellResult.setValueConf(List.of(charConf));
                }

                if (fieldTextAiValue.length() > 3) {
                    Pair<OcrText, Integer> fieldCoords = findTextCoords(pagesText, fieldTextAiValue, alreadyUsedTextCache);
                    if (fieldCoords.getLeft() != null) {
                        fieldCoords.getLeft().getOcrData().stream()
                                .map(OcrSymbol::getConfidence)
                                .forEach(cellResult.getValueConf()::add);
                        cellResult.setRegion(fieldCoords.getLeft().getTextArea());
                        if (pageIdx == -1) {
                            pageIdx = fieldCoords.getRight();
                        }
                        alreadyUsedTextCache.add(fieldCoords.getLeft());
                    }
                }

                rowResults.add(cellResult);
            }
            parsedTableData.computeIfAbsent(pageIdx == -1 ? 0 : pageIdx, pIdx -> new ArrayList<>()).add(rowResults);
        }

        List<Pair<RecoFieldResult, Integer>> result = new ArrayList<>();
        for (Map.Entry<Integer, List<List<RecoFieldResult>>> pageResults : parsedTableData.entrySet()) {
            RecoFieldResult fieldResult = RecoFieldResult.createFormless();
            fieldResult.setName(fieldName);
            fieldResult.setType(RecoFieldDataType.TABLE);
            fieldResult.getTableData().addAll(pageResults.getValue());
            fieldResult.setMatchConfidence(100);

            // TODO: Removing per-cell search area and setting per-table due to inconsistencies in cell-level areas. Should be fixed after improvements to cells handling
            List<Rectangle> cellsRects = fieldResult.getTableData().stream()
                    .flatMap(List::stream)
                    .map(RecoFieldResult::getRegion)
                    .filter(Objects::nonNull)
                    .filter(not(Rectangle::isEmpty))
                    .toList();
            Rectangle tableArea = RectangleUtils.union(cellsRects);
            fieldResult.setRegion(tableArea);
            fieldResult.getTableData().stream()
                    .flatMap(List::stream)
                    .forEach(rfr -> rfr.setRegion(null));

            result.add(Pair.of(fieldResult, pageResults.getKey()));
        }
        return result;
    }


    private static boolean isTableFieldValue(Object value) {
        if (!(value instanceof List<?> list)) {
            return false;
        }

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                return false;
            }

            for (Object key : map.keySet()) {
                if (!(key instanceof String)) {
                    return false;
                }
            }
        }

        return true;
    }


    private Pair<OcrText, Integer> findTextCoords(List<OcrText> pagesText, String text, List<OcrText> alreadyUsedText) {
        OcrText fieldText = null;
        int pageIdx;
        for (pageIdx = 0; pageIdx < pagesText.size(); pageIdx++) {
            OcrText pageText = pagesText.get(pageIdx);
            fieldText = findTextCoords(pageText, text, alreadyUsedText);
            if (fieldText != null) {
                break;
            }
        }
        return Pair.of(fieldText, pageIdx);
    }


    private OcrText findTextCoords(OcrText pageText, String text, List<OcrText> alreadyUsedText) {
        OcrText fieldText = null;
        int startPos = pageText.getText().indexOf(text);
        while (startPos >= 0) {
            OcrText candidate = pageText.substring(startPos, startPos + text.length());
            Rectangle candidateArea = candidate.getTextArea();
            List<Rectangle> intersections = alreadyUsedText.stream()
                    .map(OcrText::getTextArea)
                    .filter(candidateArea::intersects)
                    .toList();
            boolean hasMeaningfulIntersection = intersections.stream()
                    .anyMatch(textArea -> RectangleUtils.getIntersectionRatioMax(candidateArea, textArea) > 0.5);
            if (hasMeaningfulIntersection) {
                startPos = pageText.getText().indexOf(text, startPos + 1);
                continue;
            }
            fieldText = candidate;
            break;
        }
        return fieldText;
    }


    private String getRecoFormSchema() {
        if (getRecoForm().getFields().isEmpty()) {
            return null;
        }

        JsonObjectBuilder schemaBuilder = Json.createObjectBuilder();
        schemaBuilder.add("type", "Object");

        if (isNotBlank(getRecoForm().getExtractionContext())) {
            schemaBuilder.add("description", "Extraction context: " + getRecoForm().getExtractionContext());
        }

        JsonObjectBuilder propertiesBuilder = Json.createObjectBuilder();
        JsonArrayBuilder requiredFieldsBuilder = Json.createArrayBuilder();
        boolean hasRequiredFields = false;

        for (RecoField recoField : getRecoForm().getFields()) {
            if (recoField.isTypeText()) {
                propertiesBuilder.add(recoField.getName(), getRecoFieldSchemaTypeText(recoField));
            } else if (recoField.isTypeTable()) {
                propertiesBuilder.add(recoField.getName(), getRecoFieldSchemaTypeTable(recoField));
            }
            if (recoField.isRequired()) {
                requiredFieldsBuilder.add(recoField.getName());
                hasRequiredFields = true;
            }
        }

        schemaBuilder.add("properties", propertiesBuilder);

        if (hasRequiredFields && requiredFieldsBuilder.build().size() != getRecoForm().getFields().size()) {
            schemaBuilder.add("required", requiredFieldsBuilder);
        }

        return schemaBuilder.build().toString();
    }


    private JsonObjectBuilder getRecoFieldSchemaTypeText(RecoField recoField) {
        JsonObjectBuilder fieldBuilder = Json.createObjectBuilder();
        fieldBuilder.add("type", "object");

        if (isNotBlank(recoField.getProp().getAiDescription())) {
            fieldBuilder.add("description", recoField.getProp().getAiDescription());
        }


        // value
        JsonObjectBuilder valueBuilder = Json.createObjectBuilder();
        valueBuilder.add("type", "string");
        valueBuilder.add("description", "Field value");

        String dateTimeFormat = recoField.getTextProp().getDateTimeFormat();
        if (isNotBlank(dateTimeFormat)) {
            String formatValue;
            if (Strings.CI.contains(dateTimeFormat, "hh")) {
                formatValue = "DateTime in format '" + dateTimeFormat + "'";
            } else {
                formatValue = "Date in format '" + dateTimeFormat + "'";
            }
            valueBuilder.add("format", formatValue);
        }


        // Page number
        JsonObjectBuilder pageNumberBuilder = Json.createObjectBuilder()
                .add("type", "integer")
                .add("description", "1-based index of the page where the value was found");
        // Confidence level
        JsonObjectBuilder confBuilder = Json.createObjectBuilder()
                .add("type", "integer")
                .add("minimum", 0)
                .add("maximum", 100)
                .add("description", "Confidence level for located text value. MUST follow CONFIDENCE LEVEL RULES");

        fieldBuilder.add("properties", Json.createObjectBuilder()
                .add("v", valueBuilder)
                .add("c", confBuilder)
                .add("p", pageNumberBuilder));

        fieldBuilder.add("required", Json.createArrayBuilder(List.of("v", "p", "c")));

        fieldBuilder.add("additionalProperties", false);

        return fieldBuilder;
    }


    private JsonObjectBuilder getRecoFieldSchemaTypeTable(RecoField recoField) {
        JsonObjectBuilder fieldBuilder = Json.createObjectBuilder();
        fieldBuilder.add("type", "array");

        if (isNotBlank(recoField.getProp().getAiDescription())) {
            fieldBuilder.add("description", "[Table]: " + recoField.getProp().getAiDescription());
        }

        JsonObjectBuilder itemsBuilder = Json.createObjectBuilder();
        itemsBuilder.add("type", "object");

        JsonObjectBuilder columnsPropertiesBuilder = Json.createObjectBuilder();
        JsonArrayBuilder requiredColsBuilder = Json.createArrayBuilder();
        boolean hasRequiredCols = false;

        for (RecoField column : recoField.getColumns()) {
            columnsPropertiesBuilder.add(column.getName(), getRecoFieldSchemaTypeText(column));
            if (column.isRequired()) {
                requiredColsBuilder.add(column.getName());
                hasRequiredCols = true;
            }
        }

        itemsBuilder.add("properties", columnsPropertiesBuilder);

        if (hasRequiredCols && requiredColsBuilder.build().size() != recoField.getColumns().size()) {
            itemsBuilder.add("required", requiredColsBuilder);
        }

        fieldBuilder.add("items", itemsBuilder);

        return fieldBuilder;
    }


    private RecoFormResult createRecoFormResult(int pagesCount) {
        RecoFormResult recoFormResult = new RecoFormResult();
        recoFormResult.setRecoForm(getRecoForm());
        for (int pageIdx = 0; pageIdx < pagesCount; pageIdx++) {
            RecoPageResult pageResult = new RecoPageResult(getRecoMetaDoc().getCurrentPageIdx() + pageIdx);
            recoFormResult.getPages().add(pageResult);
        }
        return recoFormResult;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    public List<RecoField> getFieldsToBeMatched() {
        return _fieldsToBeMatched;
    }


    @Override
    public RecoPage getRecoPage() {
        return _recoPage;
    }

}
