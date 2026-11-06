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
package ai.eldoc.ai.service;

import ai.eldoc.ai.AiUtils;
import ai.eldoc.ai.assistants.DataExtractorAgent;
import ai.eldoc.ai.assistants.dto.reco.DocumentFragmentAiDTO;
import ai.eldoc.ai.assistants.dto.reco.RecoFormAiDTO;
import ai.eldoc.ai.config.LLMProviderConfig;
import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.ai.model.ChatModelWrapper;
import ai.eldoc.dao.DaoUtils;
import ai.eldoc.exceptions.RecoWorkflowRecognitionException;
import ai.eldoc.model.DbFileDownloadStream;
import ai.eldoc.model.DbFileRef;
import ai.eldoc.ocr.OcrEngineVLImpl;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.service.TextIndexingService;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.core.GenericType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;
import static java.util.function.Predicate.not;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Service for AI Chat
 */
@ApplicationScoped
public class AiService {

    private static final Logger LOG = LoggerFactory.getLogger(AiService.class);

    /**
     * Form name, that LLM is instructed to use, when it clearly sees document, but can't find form for it in provided forms list
     */
    public static final String IDP_MISSING_FORM = "Unlisted document type";
    private static final String IDP_MISSING_FORM_DEF = """
            - Form name: %s
                Description: Use this type when the fragment/document corresponds to a specific document type, but that type is not present in the provided document type list. For example: list contains only "Purchase order", but fragment is obviously an "Request for Quote"
            """.formatted(IDP_MISSING_FORM);

    private static final double REQUEST_MAX_SIZE_MB = 4.5;
    private static final double REQUEST_RESERVED_SIZE_MB = 0.3;
    public static final int REQUEST_IMAGES_COUNT_MAX = 20;

    @Inject
    private LLMProviderAndModelService _llmProviderAndModelService;

    @Inject
    private TextIndexingService _textIndexService;


    /**
     * Performs file segmentation/classification with provided forms list. In case agent detects document, but forms does not contain corresponding 'form' defined (i.e. agent found invoice, but no
     * 'Invoice'-like form available) - candidates will include {@link AiService#IDP_MISSING_FORM} candidate name
     *
     * @param fileId ID of the file, that is being classified (for logs)
     * @param fileContent Content of the file, that is being classified
     * @param formsList List of known/available forms
     * @param treatMissingFormAsUnknown Whether candidates, returned by Agent, but missing from Forms list, should be treated as {@link AiService#IDP_MISSING_FORM} (if {@code false}, such
     * 'hallucinated' form names will be treated as error and will trigger re-classification of the document by Agent)
     * @param requestContext {@link AiRequestContext} for interacting with Agent
     * @param retries Max number of retries in case of error or invalid answer from Agent
     * @return File content segmentation/classification result
     */
    public List<DocumentFragmentAiDTO> classifyDocument(String fileId, List<String> fileContent, Map<String, String> formsList, boolean treatMissingFormAsUnknown, AiRequestContext requestContext,
                                                        int retries) {
        return classifyDocument(fileId, fileContent, formsList, treatMissingFormAsUnknown, requestContext, 0, retries);
    }


    private List<DocumentFragmentAiDTO> classifyDocument(String fileId, List<String> fileContent, Map<String, String> formsList, boolean treatMissingFormAsUnknown, AiRequestContext requestContext,
                                                         int currentTry, int retries) {
        LOG.debug("AI RAG: File[{}]: Requested document classification", fileId);
        if (!isAiAgentsAvailable()) {
            LOG.debug("AI RAG: File[{}]: Skipping classification as Agentic model is not properly configured/enabled", fileId);
            return null;
        }
        // Load model
        ChatModelWrapper agenticModelWrapper = _llmProviderAndModelService.getIdpClassificationModel(requestContext);

        // Build AI Assistant
        DataExtractorAgent assistant = AiServices.builder(DataExtractorAgent.class)
                .chatModel(agenticModelWrapper)
                .build();

        // Build AI-friendly representation of per-page file content
        StringBuilder fileTextBuilder = new StringBuilder();
        for (int pageIdx = 0; pageIdx < fileContent.size(); pageIdx++) {
            fileTextBuilder.append(System.lineSeparator())
                    .append("<<<DOC_PAGE_IDX=").append(pageIdx + 1).append(">>>").append(System.lineSeparator())
                    .append(fileContent.get(pageIdx).trim());
        }
        String fileContentMerged = fileTextBuilder.toString();

        StringBuilder formDescriptionBuilder = new StringBuilder();
        for (Map.Entry<String, String> formDef : formsList.entrySet()) {
            // Add description of the form - we expect it to be pre-formatted and include form name, so just pass it to LLM as is
            formDescriptionBuilder.append(formDef.getValue())
                    .append(System.lineSeparator());
        }

        // Add predefined "fallback" form, that should be used, if no form for document type provided - i.e. if file invluces Invoice, but no invoice form enabled
        formDescriptionBuilder.append(IDP_MISSING_FORM_DEF);

        Result<String> answer = assistant.classifyDocuments(formDescriptionBuilder.toString(), fileContent.size(), fileContentMerged);

        String error = null;
        String responseText = answer.content();
        List<DocumentFragmentAiDTO> extractedData = null;
        try {
            extractedData = AiUtils.parseJson(responseText, new GenericType<List<DocumentFragmentAiDTO>>() {
            }.getType());

            Pair<Boolean, String> validationResult = isClassificationValid(fileId, extractedData, fileContent.size(), formsList.keySet(), treatMissingFormAsUnknown);
            if (Boolean.FALSE.equals(validationResult.getLeft())) {
                error = validationResult.getRight() + ". Classification response: " + responseText;
            }
        } catch (JsonbException e1) {
            error = "Invalid JSON: " + e1.getMessage();
            try {
                // LLM sometimes returns fragment alone, not in array, if it found only one document in file
                LOG.debug("AI RAG: File[{}]: Invalid response from LLM, trying to recover in case of single fragment", fileId);
                DocumentFragmentAiDTO fragment = AiUtils.parseJson(responseText, DocumentFragmentAiDTO.class);
                extractedData = List.of(fragment);
                error = null;
            } catch (JsonbException e2) {
                // Recovery failed - keep original error message
                LOG.debug("AI RAG: File[{}]: Recovery for single fragment failed", fileId);
            }
            if (error != null) {
                // Recovery failed - log corresponding debug lo
                LOG.debug("AI RAG: File[{}]: All recovery attempts failed", fileId);
            }
        }
        if (error != null) {
            if (currentTry < retries) {
                LOG.debug("AI RAG: File[{}]: Invalid response from LLM, retrying {} of {}. Error was: {}", fileId, currentTry + 1, retries, error);
                extractedData = classifyDocument(fileId, fileContent, formsList, treatMissingFormAsUnknown, requestContext, currentTry + 1, retries);
            } else {
                LOG.debug("AI RAG: File[{}]: Invalid response from LLM, no retries left. Last error was: {}", fileId, error);
                return null;
            }
        } else {
            LOG.trace("AI RAG: File[{}]: Classification results: {}{}", fileId, System.lineSeparator(), answer.content());
            LOG.debug("AI RAG: File[{}]: Documents classification finished", fileId);
        }

        return extractedData;
    }


    private Pair<Boolean, String> isClassificationValid(String fileId, List<DocumentFragmentAiDTO> classificationResults, int pagesCount, Collection<String> formNames,
                                                        boolean treatMissingFormAsUnknown) {
        List<Range<Integer>> classifiedRanges = classificationResults.stream()
                .map(this::getFragmentPageRange)
                .toList();
        boolean allPagesClassified = IntStream.range(1, pagesCount) // Classification result pages are 1-based
                .allMatch(pageIdx -> classifiedRanges.stream().anyMatch(range -> range.contains(pageIdx)));
        if (!allPagesClassified) {
            return Pair.of(false, "Not all pages classified");
        }

        boolean invalidPageStartEnd = classifiedRanges.stream()
                .anyMatch(fragment -> fragment.getMinimum() > fragment.getMaximum());
        if (invalidPageStartEnd) {
            return Pair.of(false, "Invalid fragments ranges - pageStart should not be bigger, than pageEnd");
        }

        boolean fragmentOutsideDocument = classifiedRanges.stream()
                .anyMatch(fragment -> fragment.getMinimum() < 0 || fragment.getMaximum() > pagesCount);
        if (fragmentOutsideDocument) {
            return Pair.of(false, "Invalid fragments ranges - fragment partially outside of the file");
        }

        boolean fragmentsOverlap = classificationResults.stream()
                .anyMatch(fragment -> classificationResults.stream().filter(not(fragment::equals))
                        .anyMatch(otherFragment -> getFragmentPageRange(fragment).isOverlappedBy(getFragmentPageRange(otherFragment))));
        if (fragmentsOverlap) {
            return Pair.of(false, "Invalid fragments ranges - fragments are partially overlap");
        }

        if (treatMissingFormAsUnknown) {
            for (DocumentFragmentAiDTO fragment : classificationResults) {
                List<String> missingForms = fragment.getCandidates().stream()
                        .filter(formName -> !AiService.IDP_MISSING_FORM.equals(formName))
                        .filter(candidate -> formNames.stream().noneMatch(knownForm -> Strings.CI.equals(knownForm, candidate)))
                        .toList();
                if (!missingForms.isEmpty()) {
                    LOG.trace("AI RAG: File[{}]: Removing missing forms and replacing with auto-classification, if enabled: {}", fileId, String.join(", ", missingForms));
                    fragment.getCandidates().removeAll(formNames);
                    if (!fragment.getCandidates().contains(AiService.IDP_MISSING_FORM)) {
                        fragment.getCandidates().add(AiService.IDP_MISSING_FORM);
                    }
                }
            }
        } else {
            List<String> notExistingForms = classificationResults.stream()
                    .map(DocumentFragmentAiDTO::getCandidates)
                    .flatMap(List::stream)
                    .filter(formName -> !AiService.IDP_MISSING_FORM.equals(formName))
                    .filter(candidate -> formNames.stream().noneMatch(knownForm -> Strings.CI.equals(knownForm, candidate)))
                    .toList();
            if (!notExistingForms.isEmpty()) {
                return Pair.of(false, "Non-existing forms suggested: " + String.join(", ", notExistingForms));
            }
        }

        return Pair.of(true, null);
    }


    private Range<Integer> getFragmentPageRange(DocumentFragmentAiDTO fragment) {
        return Range.of(fragment.getPageStart(), fragment.getPageEnd());
    }


    public RecoFormAiDTO generateRecoForm(String formId, String fileContent, AiRequestContext requestContext, int retries) {
        return generateRecoForm(formId, fileContent, requestContext, 0, retries);
    }


    public RecoFormAiDTO generateRecoForm(String formId, String fileContent, AiRequestContext requestContext, int currentTry, int retries) {
        LOG.debug("AI RAG: File[{}]: Requested RecoForm creation...", formId);
        if (!isAiAgentsAvailable()) {
            LOG.debug("AI RAG: File[{}]: Skipping extraction as AI module is not properly configured/enabled", formId);
            return null;
        }
        // Load model
        ChatModelWrapper agenticModelWrapper = _llmProviderAndModelService.getAgenticModel(requestContext);

        // Build AI Assistant
        DataExtractorAgent assistant = AiServices.builder(DataExtractorAgent.class)
                .chatModel(agenticModelWrapper)
                .build();

        Result<String> answer = assistant.generateRecoForm(RecoFormAiDTO.toJsonSchema(false), fileContent);

        String responseText = answer.content();
        RecoFormAiDTO extractedData;
        try {
            extractedData = AiUtils.parseJson(responseText, RecoFormAiDTO.class);
        } catch (JsonbException ex) {
            if (currentTry < retries) {
                LOG.debug("AI RAG: File[{}]: AI returned invalid JSON, retrying {} of {}. Error was: {}", formId, currentTry + 1, retries, ex.getMessage());
                extractedData = generateRecoForm(formId, fileContent, requestContext, currentTry + 1, retries);
            } else {
                LOG.debug("AI RAG: File[{}]: AI returned invalid JSON, no retries left. Last error was: {}", formId, ex.getMessage());
                return null;
            }
        }

        LOG.trace("AI RAG: File[{}]: RecoForm: {}{}", formId, System.lineSeparator(), answer.content());
        LOG.debug("AI RAG: File[{}]: RecoForm creation finished", formId);

        return extractedData;
    }


    public Map<String, Object> extractStructuredDataFromImage(String modelId, String outputSchema, DbFileRef fileRef, PDDocument doc, int pageStart, int pageEnd, int imagesPerRequest, AiRequestContext requestContext) throws IOException {
        LOG.debug("{}: Requested structured data extraction...", fileRef);
        if (!isVisionAvailable()) {
            LOG.debug("{}: Skipping extraction as AI module is not properly configured/enabled", fileRef);
            return null;
        }
        if (isNotBlank(modelId)) {
            LOG.debug("{}: Requested matching using model ID: {}", fileRef, modelId);
        }

        List<Map<String, Object>> extractedValues = new ArrayList<>();
        String fragmentText = null;
        int sectionStart = pageStart;

        if (LOG.isDebugEnabled() && isNotBlank(outputSchema)) {
            Path schemaTempFile = Files.createTempFile("eldoc-idp-dbg-schema-", ".txt");
            Files.writeString(schemaTempFile, outputSchema);
        }

        if (imagesPerRequest == -1) {
            imagesPerRequest = REQUEST_IMAGES_COUNT_MAX;
            LOG.debug("{}: Using default max {} images per request", fileRef, imagesPerRequest);
        } else {
            imagesPerRequest = Math.clamp(imagesPerRequest, 1, REQUEST_IMAGES_COUNT_MAX);
            LOG.debug("{}: Configured to send max {} images per request", fileRef, imagesPerRequest);
        }

        List<BufferedImage> biList = new ArrayList<>();
        double alreadyProcessedPagesSizeMB = 0;
        double requestMaxSize = getRequestImagesSizeMax(_llmProviderAndModelService.getIdpExtractionModelConfig().getLeft());
        boolean fullFragment = true;
        for (int pageIdx = 0; pageIdx < (pageEnd - pageStart); pageIdx++) {
            BufferedImage pageImage = PdfUtils.renderOrExtractDocPageImage(doc, pageStart + pageIdx, 240);
            if (LOG.isTraceEnabled()) {
                Path imageTempFile = Files.createTempFile("eldoc-idp-dbg-img-" + pageIdx + "-", ".png");
                ImageIO.write(pageImage, "png", imageTempFile.toFile());
            }
            double estimatedImageSizeMB = OcrEngineVLImpl.getSmallestImageSize(pageImage);
            boolean requestSizeWouldBeExceeded = alreadyProcessedPagesSizeMB + estimatedImageSizeMB > requestMaxSize;
            boolean requestImagesCountWouldBeExceeded = biList.size() + 1 > imagesPerRequest;
            if ((requestSizeWouldBeExceeded || requestImagesCountWouldBeExceeded) && !biList.isEmpty()) {
                // IMPORTANT: `!biList.isEmpty()` check is necessary to avoid sending an empty list. Single oversized image should be sent on the next iteration.
                // Estimated request size with allowed images compression might exceed the request size limit
                if (fullFragment) {
                    // Log fallback only on first occurrence
                    LOG.debug("{}: Requesting full file data extraction would exceed request size limit - fallback to chunked structured data extraction", fileRef);
                }
                fullFragment = false;
                if (fragmentText == null) {
                    fragmentText = getFileTextIndexForIDP(fileRef, pageStart, pageEnd, requestContext);
                }
                Map<String, Object> extractedData = fitFileFragmentAndExtractFields(fileRef, modelId, outputSchema, biList, fragmentText, sectionStart, pageStart + pageIdx - 1, fullFragment, requestContext);
                if (!CollectionUtils.isEmpty(extractedData.values())) {
                    extractedValues.add(extractedData);
                }
                biList.clear();
                alreadyProcessedPagesSizeMB = 0;
                sectionStart = pageStart + pageIdx;
            }
            biList.add(pageImage);
            alreadyProcessedPagesSizeMB += estimatedImageSizeMB;
        }

        if (!biList.isEmpty()) {
            if (!fullFragment && fragmentText == null) {
                fragmentText = getFileTextIndexForIDP(fileRef, pageStart, pageEnd, requestContext);
            }
            Map<String, Object> extractedData = fitFileFragmentAndExtractFields(fileRef, modelId, outputSchema, biList, fragmentText, sectionStart, pageEnd - 1, fullFragment, requestContext);
            if (!CollectionUtils.isEmpty(extractedData.values())) {
                extractedValues.add(extractedData);
            }
        }

        LOG.debug("{}: Extracting structured data finished", fileRef);

        return mergeExtractedChunkValues(extractedValues);
    }


    private String getFileTextIndexForIDP(DbFileRef fileRef, int pageStart, int pageEnd, AiRequestContext requestContext) throws IOException {
        String textIndex = _textIndexService.getFileTextIndexContent(fileRef, pageStart, pageEnd, true);
        if (isNotBlank(textIndex)) {
            return textIndex;
        }
        try (DbFileDownloadStream is = DaoUtils.getFileAwareDao(fileRef).getFilesDao().getFileDownloadStream(fileRef)) {
            Pair<List<String>, Integer> newTextIndex = _textIndexService.extractText(is.getDbFile().getFileName(), is, true, requestContext);
            _textIndexService.uploadTextIndexFile(is.getDbFile(), newTextIndex.getLeft(), newTextIndex.getRight(), true);
            StringBuilder fileTextBuilder = new StringBuilder();
            for (int pageIdx = pageStart; pageIdx < pageEnd; pageIdx++) {
                fileTextBuilder.append(System.lineSeparator())
                        .append("<<<DOC_PAGE_IDX=").append(pageIdx + 1).append(">>>").append(System.lineSeparator())
                        .append(newTextIndex.getLeft().get(pageIdx).trim()); // newTextIndex contains text index for whole file, not only for requested fragment
            }
            return fileTextBuilder.toString();
        }
    }


    private Map<String, Object> mergeExtractedChunkValues(List<Map<String, Object>> extractedValues) {
        Map<String, Object> extractedData = new HashMap<>();
        for (Map<String, Object> chunkResults : extractedValues) {
            for (Map.Entry<String, Object> extractedValue : chunkResults.entrySet()) {
                String key = extractedValue.getKey();
                Object value = normalizeValue(extractedValue.getValue());

                if (value == null) {
                    continue;
                }

                extractedData.merge(key, value, this::mergeValues);
            }
        }
        return extractedData;
    }


    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }

                Object normalizedValue = normalizeValue(entry.getValue());

                if (normalizedValue != null) {
                    normalized.put(entry.getKey().toString(), normalizedValue);
                }
            }

            return normalized;
        }

        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::normalizeValue)
                    .filter(Objects::nonNull)
                    .toList();
        }

        return value.toString();
    }


    private Object mergeValues(Object existingValue, Object newValue) {
        if (existingValue == null) {
            return newValue;
        }

        if (newValue == null) {
            return existingValue;
        }

        if (existingValue instanceof Map<?, ?> existingMap && newValue instanceof Map<?, ?> newMap) {
            return mergeMaps(existingMap, newMap);
        }

        if (existingValue instanceof List<?> existingList && newValue instanceof List<?> newList) {
            return mergeLists(existingList, newList);
        }

        if (existingValue instanceof List<?> existingList) {
            List<Object> result = new ArrayList<>(existingList);
            result.add(newValue);
            return result;
        }

        if (newValue instanceof List<?> newList) {
            List<Object> result = new ArrayList<>();
            result.add(existingValue);
            result.addAll(newList);
            return result;
        }

        if (existingValue instanceof Map<?, ?> existingMap) {
            return existingMap;
        }

        if (newValue instanceof Map<?, ?> newMap) {
            return newMap;
        }

        if (Objects.equals(existingValue, newValue)) {
            return existingValue;
        }

        List<Object> result = new ArrayList<>();
        result.add(existingValue);
        result.add(newValue);
        return result;
    }


    private List<Object> mergeLists(List<?> list1, List<?> list2) {
        List<Object> result = new ArrayList<>(list1.size() + list2.size());
        result.addAll(list1);
        result.addAll(list2);
        return result;
    }


    private Map<String, Object> mergeMaps(Map<?, ?> map1, Map<?, ?> map2) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : map1.entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey().toString(), normalizeValue(entry.getValue()));
            }
        }

        for (Map.Entry<?, ?> entry : map2.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            String key = entry.getKey().toString();
            Object value = normalizeValue(entry.getValue());

            if (value != null) {
                result.merge(key, value, this::mergeValues);
            }
        }

        return result;
    }


    private Map<String, Object> fitFileFragmentAndExtractFields(
            DbFileRef fileRef, String modelId, String outputSchema,
            List<BufferedImage> biList, String fragmentText, int sectionStart, int sectionEnd,
            boolean isFullFragment,
            AiRequestContext requestContext) {
        double requestMaxSize = getRequestImagesSizeMax(_llmProviderAndModelService.getIdpExtractionModelConfig(modelId).getLeft());
        double imagesMaxSizeMB = requestMaxSize;
        double fragmentTextSizeMB = -1;
        if (isNotBlank(fragmentText)) {
            fragmentTextSizeMB = getTextSizeMB(fragmentText);
            imagesMaxSizeMB = requestMaxSize - fragmentTextSizeMB;
            LOG.debug("{}: Reducing configured request size limit to fit fragment text, fragmentTextSize={}MB, final imagesMaxSize={}MB", fileRef.getId(), fragmentTextSizeMB, imagesMaxSizeMB);
        }
        if (imagesMaxSizeMB <= 0) {
            double fallbackRequestSizeLimit = REQUEST_RESERVED_SIZE_MB / 2;
            LOG.warn("{}: Fragment text alone exceeds max request size. Request max size: {}MB, fragment text size: {}MB, using fallback images size limit {}MB (may still exceed allowed request size)",
                    fileRef.getId(), requestMaxSize, fragmentTextSizeMB, fallbackRequestSizeLimit);
            imagesMaxSizeMB = fallbackRequestSizeLimit;
        }
        List<ImageContent> pageImagesContent = OcrEngineVLImpl.getImageContent(biList, "idp", imagesMaxSizeMB);
        double totalRequestLength = OcrEngineVLImpl.getImageItemSizeMb(pageImagesContent);

        if (totalRequestLength < imagesMaxSizeMB) {
            // Images already fit into requested size limits
            LOG.debug("{}: Provided images total size fit into request size limit, extracting data, totalRequestLength={}MB, imagesMaxSize={}MB", fileRef.getId(), totalRequestLength, imagesMaxSizeMB);
            return extractImagesData(fileRef, modelId, outputSchema, pageImagesContent, fragmentText, sectionStart, sectionEnd, isFullFragment, requestContext, 0, 3);
        }

        // Provided images total size does not fit into requested size limits - try fitting
        double extraSizeRatio = totalRequestLength / imagesMaxSizeMB;
        boolean sizeExceeded = extraSizeRatio > 1;
        boolean forceFullFileFit = isFullFragment && sizeExceeded && extraSizeRatio < 1.2;
        boolean forceSmallDiffFit = sizeExceeded && extraSizeRatio < 1.05;
        if (forceFullFileFit || forceSmallDiffFit || biList.size() == 1) {
            LOG.debug("{}: Compressed images size are still to big - force fitting: sizeRatio={}", fileRef.getId(), extraSizeRatio);
            // Image still does not fit into requested size limits, but this is full fragment with minor size difference - force fit to keep extraction accuracy
            pageImagesContent = OcrEngineVLImpl.getImageContent(biList, fileRef.getId(), imagesMaxSizeMB, true);
            totalRequestLength = OcrEngineVLImpl.getImageItemSizeMb(pageImagesContent);
            if (totalRequestLength < imagesMaxSizeMB) {
                LOG.debug("{}: Final images size after force fitting: {}MB, limit: {}MB", fileRef.getId(), totalRequestLength, imagesMaxSizeMB);
                return extractImagesData(fileRef, modelId, outputSchema, pageImagesContent, fragmentText, sectionStart, sectionEnd, isFullFragment, requestContext, 0, 3);
            } else if (biList.size() == 1) {
                LOG.warn("{}: Final images size after force fitting: {}MB, limit: {}MB - single image is still too big even at {} scale, sending as is", fileRef.getId(), totalRequestLength, imagesMaxSizeMB, OcrEngineVLImpl.getMinImageScaleForce());
                return extractImagesData(fileRef, modelId, outputSchema, pageImagesContent, fragmentText, sectionStart, sectionEnd, isFullFragment, requestContext, 0, 3);
            } else {
                LOG.debug("{}: Small-extra fitting failed, continue with chunk splitting. Request size after force fitting: {}, limit: {}", fileRef.getId(), totalRequestLength, imagesMaxSizeMB);
            }
        }

        LOG.debug("{}: Compressed images size are still to big - reducing images count: sizeRatio={}", fileRef.getId(), extraSizeRatio);
        List<Map<String, Object>> extractedValues = new ArrayList<>();

        int chunkStart = 0;
        while (chunkStart < pageImagesContent.size()) {
            int chunkEndExclusive = chunkStart + 1;

            while (chunkEndExclusive < pageImagesContent.size() && OcrEngineVLImpl.getImageItemSizeMb(pageImagesContent.subList(chunkStart, chunkEndExclusive + 1)) < imagesMaxSizeMB) {
                chunkEndExclusive++;
            }

            LOG.trace("{}: Processing chunk: chunkStart={}, chunkEnd={}, currentFragmentSize={}", fileRef.getId(), chunkStart, chunkEndExclusive - 1, biList.size());

            int chunkPageStart = sectionStart + chunkStart;
            int chunkPageEnd = sectionStart + chunkEndExclusive - 1;

            List<ImageContent> chunkImagesContent = pageImagesContent.subList(chunkStart, chunkEndExclusive);
            double chunkRequestLength = OcrEngineVLImpl.getImageItemSizeMb(chunkImagesContent);

            Map<String, Object> chunkData;
            if (chunkRequestLength < imagesMaxSizeMB) {
                // We already have fitting chunk - reuse processed images
                LOG.debug("{}: Created chunk fits request limit, chunk size: {}MB, limit: {}MB", fileRef.getId(), chunkRequestLength, imagesMaxSizeMB);
                chunkData = extractImagesData(fileRef, modelId, outputSchema, chunkImagesContent, fragmentText, chunkPageStart, chunkPageEnd, false, requestContext, 0, 3);
            } else if (chunkStart == 0 && chunkEndExclusive == biList.size() && biList.size() > 1) {
                // Prevent same-range recursion: split current fragment explicitly.
                int splitIndex = Math.max(1, biList.size() / 2);

                LOG.debug("{}: Chunk splitting made not enough progress, force-splitting current fragment: left=0-{}, right={}-{}", fileRef.getId(), splitIndex - 1, splitIndex, biList.size() - 1);

                List<Map<String, Object>> splitValues = new ArrayList<>();

                Map<String, Object> leftChunkData = fitFileFragmentAndExtractFields(fileRef, modelId, outputSchema, biList.subList(0, splitIndex), fragmentText, sectionStart, sectionStart + splitIndex - 1, false, requestContext);
                if (!CollectionUtils.isEmpty(leftChunkData.values())) {
                    splitValues.add(leftChunkData);
                }

                Map<String, Object> rightChunkData = fitFileFragmentAndExtractFields(fileRef, modelId, outputSchema, biList.subList(splitIndex, biList.size()), fragmentText, sectionStart + splitIndex, sectionEnd, false, requestContext);
                if (!CollectionUtils.isEmpty(rightChunkData.values())) {
                    splitValues.add(rightChunkData);
                }

                chunkData = mergeExtractedChunkValues(splitValues);
            } else {
                // Chunk is not fitting - recurse with smaller BufferedImage subList
                chunkData = fitFileFragmentAndExtractFields(fileRef, modelId, outputSchema, biList.subList(chunkStart, chunkEndExclusive), fragmentText, chunkPageStart, chunkPageEnd, false, requestContext);
            }

            if (!CollectionUtils.isEmpty(chunkData.values())) {
                extractedValues.add(chunkData);
            }

            chunkStart = chunkEndExclusive;
        }

        // Merge results from each chunk and return as extracted content for current fragment
        return mergeExtractedChunkValues(extractedValues);
    }


    /**
     * Performs actual data extraction from provided images.<br>
     * <b>IMPORTANT:</b> This method requires that provided images are already preprocessed and fit into request size limits, if applicable
     *
     * @param fileRef {@link DbFileRef} of the file to extract data from
     * @param modelId ID of the LLM model to use for extraction
     * @param outputSchema RecoForm schema to use for extraction
     * @param pageImagesContent List of {@link ImageContent} to extract data from
     * @param fileContent Prepared content of the whole file, sent to the model with pages images if provided page does not represent full file content
     * @param sectionStart Index of the first of the provided images (inclusive)
     * @param sectionEnd Index of the end of the provided images (exclusive)
     * @param isFullFragment {@code true} if provided images represent full file content, {@code false} otherwise
     * @param requestContext {@link AiRequestContext} to use for extraction
     * @param currentAttempt Current attempt number
     * @param retries Total number of retries allowed
     * @return Extracted data as a map
     */
    private Map<String, Object> extractImagesData(
            DbFileRef fileRef, String modelId, String outputSchema,
            List<ImageContent> pageImagesContent, String fileContent, int sectionStart, int sectionEnd,
            boolean isFullFragment,
            AiRequestContext requestContext,
            int currentAttempt, int retries) {
        double imagesLength = OcrEngineVLImpl.getImageItemSizeMb(pageImagesContent);
        double requestMaxSize = getRequestImagesSizeMax(_llmProviderAndModelService.getIdpExtractionModelConfig(modelId).getLeft());
        LOG.debug("{}: Extracting structured data from images on pages: {}-{}, imagesSize: {}, fileContentSize: {}, limit: {}...", fileRef, sectionStart, sectionEnd, imagesLength, getTextSizeMB(fileContent), requestMaxSize);
        // Load model
        ChatModelWrapper visionModelWrapper = _llmProviderAndModelService.getIdpExtractionModel(modelId, requestContext);

        // Build AI Assistant
        DataExtractorAgent assistant = AiServices.builder(DataExtractorAgent.class)
                .chatModel(visionModelWrapper)
                .build();

        Result<String> answer;
        if (outputSchema == null) {
            // No schema with required fields provided - meaning we need to extract all available data
            answer = isFullFragment ? assistant.extractStructuredDataAll(pageImagesContent) : assistant.extractStructuredDataAllInRange(pageImagesContent, fileContent, sectionStart + 1, sectionEnd + 1);
        } else {
            // Schema with required fields and their format provided - extract requested data
            answer = isFullFragment ? assistant.extractStructuredData(outputSchema, pageImagesContent) : assistant.extractStructuredDataInRange(outputSchema, fileContent, pageImagesContent, sectionStart + 1, sectionEnd + 1);
        }
        String responseText = answer.content();
        Map<String, Object> extractedData;
        try {
            extractedData = AiUtils.parseJson(responseText, new GenericType<Map<String, Object>>() {
            }.getType());
        } catch (JsonbException ex) {
            if (currentAttempt < retries) {
                LOG.debug("{}: Error extracting data from AI response (invalid JSON), retrying {} of {}. {}", fileRef, currentAttempt + 1, retries, ex.getMessage());
                extractedData = extractImagesData(fileRef, modelId, outputSchema, pageImagesContent, fileContent, sectionStart, sectionEnd, isFullFragment, requestContext, currentAttempt + 1, retries);
            } else {
                LOG.warn("{}: Error extracting data from AI response (invalid JSON) after {} retries, no retries left. {}", fileRef, retries, ex.getMessage());
                throw new RecoWorkflowRecognitionException("Error processing AI/LLM extracted values for pages in range: " + (sectionStart + 1) + "-" + (sectionEnd + 1), ex);
            }
        }
        LOG.trace("{}: Extracted data: {}{}", fileRef, System.lineSeparator(), answer.content());
        return extractedData;
    }


    /**
     * Returns length of the text in MB, how it will be sent to LLM (base64-encoded)
     *
     * @param fragmentText Text to calculate the size of
     * @return Size in MB
     */
    private double getTextSizeMB(String fragmentText) {
        if (isBlank(fragmentText)) {
            return 0;
        }
        return (fragmentText.getBytes(StandardCharsets.UTF_8).length + 2) * 4.0 / 3.0 / 1_000_000.0;
    }


    public OcrEngineVLImpl createVLOcrEngine(AiRequestContext requestContext) {
        return new OcrEngineVLImpl(_llmProviderAndModelService.getVisionModel(requestContext), requestContext, getRequestImagesSizeMax(_llmProviderAndModelService.getVisionModelConfig().getLeft()));
    }


    private double getRequestImagesSizeMax(LLMProviderConfig config) {
        Double configuredRequestMaxSizeMB = config.getRequestMaxSizeMB();
        if (configuredRequestMaxSizeMB == null || configuredRequestMaxSizeMB == 0) {
            return REQUEST_MAX_SIZE_MB - REQUEST_RESERVED_SIZE_MB;
        } else if (configuredRequestMaxSizeMB == -1) {
            return Double.MAX_VALUE;
        }
        return configuredRequestMaxSizeMB - REQUEST_RESERVED_SIZE_MB;
    }


    public boolean isClassificationAvailable() {
        return isAiAgentsAvailable();
    }


    /**
     * Checks if AI Vision is available by checking that AI Agent module is licensed and the required model is properly configured
     *
     * @return {@code true} if AI Vision is available or {@code false} otherwise
     */
    public boolean isVisionAvailable() {
        return _llmProviderAndModelService.getVisionModelConfig() != null;
    }


    /**
     * Checks if AI Agents is available by checking that AI Agent module is licensed and the required model is properly configured
     *
     * @return {@code true} if AI Agents is available or {@code false} otherwise
     */
    public boolean isAiAgentsAvailable() {
        return _llmProviderAndModelService.getAgentModelConfig() != null;
    }


    //
    // General Service methods --------------------------------------------------------------------
    //


    public static AiService getInstance() {
        return CDI.current().select(AiService.class).get();
    }

}
