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
package ai.eldoc.service;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.exceptions.ScriptingException;
import ai.eldoc.exceptions.TimeoutException;
import ai.eldoc.model.CodeSnippet;
import ai.eldoc.model.CustomField;
import ai.eldoc.model.reco.RecoData;
import ai.eldoc.util.JsonbUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.JsonbException;
import org.apache.commons.collections4.CollectionUtils;
import org.graalvm.polyglot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static ai.eldoc.service.CodeSnippetService.JS_TIMEOUT_SEC;
import static ai.eldoc.service.CodeSnippetService.SCRIPT_LANG;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Recognition Post-Processing Class
 */
@ApplicationScoped
public class RecoPostProcessorService {

    private static final Logger LOG = LoggerFactory.getLogger(RecoPostProcessorService.class);

    public static final String ELDOC_JVM_OPTION_POSTPROC_TIMEOUT_SEC = "ai.eldoc.idp.postProc.timeoutSec";
    public static final int IDP_JS_TIMEOUT_SEC = Integer.getInteger(ELDOC_JVM_OPTION_POSTPROC_TIMEOUT_SEC, JS_TIMEOUT_SEC);

    private static final String VALUE_VAR_NAME = "field";
    private static final String RECODATA_VAR_NAME = "recoData";
    private static final String RECODATA_FIELD_DATA = "fields";
    private static final String FUNCTION_NAME = "postProcessor";
    private static final String SCOPE_NAME = "scope";

    private static final int FIELD_VALIDATED_CONF = 99;
    private static final int FIELD_NOT_VALIDATED_CONF = 0;
    private static final String SCRIPT_BASE_DIR = "codeRepo";

    @Inject
    private CodeSnippetService _codeSnippetService;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Performs execution of provided post-processing function, using GraalVM scripting engine (JavaScript is used as scripting language)
     *
     * @param cf
     * @param postProcessorFunc Body of the JS function to be executed
     * @param value Value of the RecoField (TEXT)
     * @param lang
     * @param scriptScope Global script scope, used to exchange data between different post-processing functions (other fields or global form post-processing)
     * @param os {@link OutputStream} to be used for printing script console output (i.e. console.log())
     * @return new RecoField value, if post-processing function execution succeeded and returned value, or old value, if nothing returned from script.
     * Type of the returned value is RecoField datatype-dependent in same way, as value parameter
     * @throws TimeoutException in case script execution was interrupted due to timeout
     * @throws ScriptingException in case script returned value of wrong type or script was trying to override scope variable instead of modifying it
     */
    public String postProcessTextField(
            CustomField cf,
            String postProcessorFunc,
            String value,
            String lang,
            Map<String, Object> scriptScope,
            OutputStream os) throws TimeoutException, ScriptingException {
        try {
            return (String) postProcessField(cf, postProcessorFunc, value, lang, scriptScope, os);
        } catch (ClassCastException ex) {
            throw new ScriptingException("Field script should not overwrite script context, modify it's properties instead");
        }
    }


    /**
     * Performs execution of provided post-processing function, using GraalVM scripting engine (JavaScript is used as scripting language)
     *
     * @param cf
     * @param postProcessorFunc Body of the JS function to be executed
     * @param value Value of the RecoField (TABLE), represented as List (rows, without header row) of List&lt;String&gt; (columns)
     * @param lang
     * @param scriptScope Global script scope, used to exchange data between different post-processing functions (other fields or global form post-processing)
     * @param os {@link OutputStream} to be used for printing script console output (i.e. console.log())
     * @return new RecoField value, if post-processing function execution succeeded and returned value, or old value, if nothing returned from script.
     * Type of the returned value is RecoField datatype-dependent in same way, as value parameter
     * @throws TimeoutException in case script execution was interrupted due to timeout
     * @throws ScriptingException in case script returned value of wrong type or script was trying to override scope variable instead of modifying it
     */
    public List<List<String>> postProcessTableField(
            CustomField cf,
            String postProcessorFunc,
            List<List<String>> value,
            String lang,
            Map<String, Object> scriptScope,
            OutputStream os) throws TimeoutException, ScriptingException {
        try {
            return (List<List<String>>) postProcessField(cf, postProcessorFunc, value, lang, scriptScope, os);
        } catch (ClassCastException ex) {
            throw new ScriptingException("Field script should not overwrite script context, modify it's properties instead");
        }
    }


    /**
     * Performs execution of provided post-processing function, using GraalVM scripting engine (JavaScript is used as scripting language)
     *
     * @param cf
     * @param postProcessorFunc Body of the JS function to be executed
     * @param value Value of the RecoField (OMR)
     * @param lang
     * @param scriptScope Global script scope, used to exchange data between different post-processing functions (other fields or global form post-processing)
     * @param os {@link OutputStream} to be used for printing script console output (i.e. console.log())
     * @return new RecoField value, if post-processing function execution succeeded and returned value, or old value, if nothing returned from script.
     * Type of the returned value is RecoField datatype-dependent in same way, as value parameter
     * @throws TimeoutException in case script execution was interrupted due to timeout
     * @throws ScriptingException in case script returned value of wrong type or script was trying to override scope variable instead of modifying it
     */
    public List<Boolean> postProcessOmrField(
            CustomField cf,
            String postProcessorFunc,
            List<Boolean> value,
            String lang,
            Map<String, Object> scriptScope,
            OutputStream os) throws TimeoutException, ScriptingException {
        try {
            return (List<Boolean>) postProcessField(cf, postProcessorFunc, value, lang, scriptScope, os);
        } catch (ClassCastException ex) {
            throw new ScriptingException("Field script should not overwrite script context, modify it's properties instead");
        }
    }


    /**
     * Performs post-processing of the field
     *
     * @param dataType
     * @param postProcessorFunc
     * @param value
     * @param confThreshold
     * @param conf
     * @param scriptScope
     * @param os
     * @return
     * @throws TimeoutException
     * @throws ScriptingException
     */
    private Object postProcessField(
            CustomField cf,
            String postProcessorFunc,
            Object value,
            String lang,
            Map<String, Object> scriptScope,
            OutputStream os) throws TimeoutException, ScriptingException {

        List<CodeSnippet> codeSnippetList = _codeSnippetService.findByType(CodeSnippet.TYPE_RECO);
        String sharedCode = "";
        if (CollectionUtils.isNotEmpty(codeSnippetList)) {
            try {
                sharedCode = codeSnippetList.get(0).getCodeDecoded();
            } catch (ParseException ex) {
                LOG.error(null, ex);
            }
        }

        String script = "var " + SCOPE_NAME + " = JSON.parse(scriptScope); "
                + "function " + FUNCTION_NAME + "Inner(" + VALUE_VAR_NAME + ") { "
                + VALUE_VAR_NAME + " = JSON.parse(" + VALUE_VAR_NAME + "); " + System.lineSeparator()
                + sharedCode + System.lineSeparator()
                + postProcessorFunc + System.lineSeparator()
                + " }"
                + "function " + FUNCTION_NAME + "(" + VALUE_VAR_NAME + ") {"
                + "return JSON.stringify(" + FUNCTION_NAME + "Inner(" + VALUE_VAR_NAME + "));"
                + "}";

        // Create Graal.js script engine
        try (Context context = Context.newBuilder(SCRIPT_LANG)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> className.equals("ai.eldoc.reco.Bitap") || className.equals("ai.eldoc.reco.Bitap.BitapResult"))
                .option("engine.WarnInterpreterOnly", "false")
                .out(os)
                .build()) {
            Source source = Source.newBuilder(SCRIPT_LANG, script, "postProcessor.js").build();
            context.getBindings(SCRIPT_LANG).putMember("scriptScope", JsonbUtils.getInstance().toJson(scriptScope));

            // Compile script parameters
            PostProcessorCustomField postProcFld = new PostProcessorCustomField(cf, value, lang);
            String scriptParam = JsonbUtils.getInstance().toJson(postProcFld);

            // Set timeout - forcibly close Graal.js context, if timeout reached
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    context.close(true);
                }
            }, IDP_JS_TIMEOUT_SEC * 1000);

            // Add predefined includes
            addCodeRepoIncludes(context);

            // Compile postprocessor
            context.eval(source);

            // Execute postprocessor code
            Value scriptResult = context.getBindings(SCRIPT_LANG).getMember(FUNCTION_NAME).execute(scriptParam);
            Value newScopeValue = context.getBindings(SCRIPT_LANG).getMember(SCOPE_NAME);
            try {
                Map<String, Object> newScope = newScopeValue.as(new TypeLiteral<Map<String, Object>>() {
                });
                scriptScope.clear();
                scriptScope.putAll(newScope);
            } catch (ClassCastException ex) {
                throw new ScriptingException("Field script should not overwrite script context, modify it's properties instead");
            }

            if (scriptResult.isNull()) {
                // Script returned no value - return original value
                return value;
            }

            // Stringify return value
            String resultValueString = scriptResult.asString();
            PostProcessorCustomField resultCf = null;
            try {
                resultCf = JsonbUtils.getInstance().fromJson(resultValueString, PostProcessorCustomField.class);
            } catch (JsonbException ex) {
                throw new ScriptingException("Returned value has a wrong structure", ex);
            }

            updateCustomFieldConfidence(cf, resultCf, value, resultCf.getValue(), lang);

            return getWrappedOrDefault(cf, resultCf.getValue());
        } catch (PolyglotException ex) {
            if (ex.isCancelled()) {
                throw new TimeoutException("PostProcessing interrupted due to timeout (" + IDP_JS_TIMEOUT_SEC + "sec per field)", ex);
            } else {
                throw new ScriptingException("Error during post-processing script execution", ex);
            }
        } catch (IOException ex) {
            throw new ElDocRuntimeException("Error preparing postprocessor script for execution", ex);
        }
    }


    /**
     * Performs validation of the post-processing settings on the field
     *
     * @param postProcessorFunc
     * @param scriptScope
     * @param os
     * @return
     * @throws TimeoutException
     */
    public boolean validateFieldPostProcessor(String postProcessorFunc, Map<String, Object> scriptScope, OutputStream os) throws PolyglotException, TimeoutException {
        // Create Graal.js script engine
        try (Context context = Context.newBuilder(SCRIPT_LANG)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> className.equals("ai.eldoc.reco.Bitap") || className.equals("ai.eldoc.reco.Bitap.BitapResult"))
                .option("engine.WarnInterpreterOnly", "false")
                .out(os)
                .build()) {
            Source source = Source.newBuilder(SCRIPT_LANG, "function test(" + VALUE_VAR_NAME + ") {" + System.lineSeparator()
                    + postProcessorFunc + System.lineSeparator()
                    + "}", "postProcessor.js").build();
            context.getBindings(SCRIPT_LANG).putMember("scriptScope", JsonbUtils.getInstance().toJson(scriptScope));

            // Set timeout - forcibly close Graal.js context, if timeout reached
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    context.close(true);
                }
            }, IDP_JS_TIMEOUT_SEC * 1000);

            // Add predefined includes
            addCodeRepoIncludes(context);

            // Compile postprocessor
            context.eval(source);
            return true;
        } catch (PolyglotException ex) {
            if (ex.isCancelled()) {
                throw new TimeoutException("PostProcessing interrupted due to timeout."
                        + "Current timeout per field set to " + IDP_JS_TIMEOUT_SEC + " seconds.",
                        ex);
            } else {
                throw new ScriptingException("Error during post-processing script execution", ex);
            }
        } catch (IOException ex) {
            throw new ElDocRuntimeException("Error preparing postprocessor script for execution", ex);
        }
    }


    /**
     * Performs post-processing of the whole RecoData
     *
     * @param postProcessorFunc
     * @param recoData
     * @param lang
     * @param scriptScope
     * @param os
     * @return
     * @throws TimeoutException
     */
    public boolean postProcessRecoData(
            String postProcessorFunc,
            RecoData recoData,
            String lang,
            Map<String, Object> scriptScope,
            OutputStream os) throws TimeoutException {

        List<CodeSnippet> codeSnippetList = _codeSnippetService.findByType(CodeSnippet.TYPE_RECO);
        String sharedCode = "";
        if (CollectionUtils.isNotEmpty(codeSnippetList)) {
            try {
                sharedCode = codeSnippetList.get(0).getCodeDecoded();
            } catch (ParseException ex) {
                LOG.error(null, ex);
            }
        }

        String script = "var " + SCOPE_NAME + " = JSON.parse(scriptScope);"
                + "function " + FUNCTION_NAME + " (" + RECODATA_VAR_NAME + ") {"
                + RECODATA_VAR_NAME + " = JSON.parse(" + RECODATA_VAR_NAME + "); " + System.lineSeparator()
                + sharedCode + System.lineSeparator()
                + postProcessorFunc + System.lineSeparator()
                + " }";

        String stringify = "function stringify(value) {return JSON.stringify(value);}";

        // Create Graal.js script engine
        try (Context context = Context.newBuilder(SCRIPT_LANG)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> className.equals("ai.eldoc.reco.Bitap") || className.equals("ai.eldoc.reco.Bitap.BitapResult"))
                .option("engine.WarnInterpreterOnly", "false")
                .out(os)
                .build()) {
            Source source = Source.newBuilder(SCRIPT_LANG, script, "postProcessor.js").build();
            Source stringifySource = Source.newBuilder(SCRIPT_LANG, stringify, "stringify.js").build();
            context.getBindings(SCRIPT_LANG).putMember("scriptScope", JsonbUtils.getInstance().toJson(scriptScope));

            // Set timeout - forcibly close Graal.js context, if timeout reached
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    context.close(true);
                }
            }, IDP_JS_TIMEOUT_SEC * 1000);

            // Add predefined includes
            addCodeRepoIncludes(context);

            // Compile postprocessor
            context.eval(source);

            // Execute postprocessor code
            Value scriptResult = context.getBindings(SCRIPT_LANG).getMember(FUNCTION_NAME).execute(recoDataToJsonConverter(recoData, lang, false));
            Map<String, Object> newScope = context.getBindings(SCRIPT_LANG).getMember(SCOPE_NAME).as(new TypeLiteral<Map<String, Object>>() {
            });
            scriptScope.clear();
            scriptScope.putAll(newScope);

            if (scriptResult.isNull()) {
                // Script returned no value - keep original value
                return false;
            }

            // Extract processing results
            try {

                // Transfer only required values from script to original RecoData, if needed
                AtomicBoolean updated = new AtomicBoolean(false);

                boolean newValidationState = scriptResult.hasMember(RecoData._VALIDATED) ? scriptResult.getMember(RecoData._VALIDATED).asBoolean() : recoData.isValidated();

                Value resultCfData = scriptResult.getMember(RECODATA_FIELD_DATA);

                // Stringify return value
                context.eval(stringifySource);
                Value resultJsonValue = context.getBindings(SCRIPT_LANG).getMember("stringify").execute(resultCfData);
                String resultJson = resultJsonValue.asString();
                List<PostProcessorCustomField> resultCf = JsonbUtils.getInstance().fromJson(resultJson, new TypeLiteral<List<PostProcessorCustomField>>() {
                }.getType());

                if (resultCf.size() != recoData.getCfDef().size()) {
                    // If post-processing returned wrong number of fields - throw exception
                    throw new ScriptingException("Form script should return proper recoData as result");
                }

                for (int cfIdx = 0; cfIdx < resultCfData.getArraySize(); cfIdx++) {
                    PostProcessorCustomField newCfResult = resultCf.get(cfIdx);
                    CustomField customField = recoData.getCfDef().get(cfIdx);
                    String dsFieldName = recoData.getCfDsFieldName(customField);
                    Object newRecoValue = newCfResult.getValue();
                    Object recoValue = recoData.getCfData().get(dsFieldName);
                    boolean validationStatusChanged = isCfValidationStatusChanged(customField, newCfResult);
                    boolean valueChanged = !Objects.equals(getWrappedOrDefault(customField, recoValue), newRecoValue);
                    // Update RecoData value and confidence
                    if (valueChanged || validationStatusChanged) {
                        boolean fieldValidationState = updateCustomFieldConfidence(customField, newCfResult, recoValue, newRecoValue, lang);
                        newValidationState = newValidationState && fieldValidationState;
                        recoData.getCfData().put(dsFieldName, newRecoValue);
                        updated.set(true);
                    }
                }

                if (recoData.isValidated() != newValidationState) {
                    recoData.setValidated(newValidationState);
                    updated.set(true);
                }

                return updated.get();
            } catch (ClassCastException ex) {
                throw new ScriptingException("Form script should return proper recoData as result", ex);
            }
        } catch (PolyglotException ex) {
            if (ex.isCancelled()) {
                throw new TimeoutException("PostProcessing interrupted due to timeout."
                        + "Current timeout per field set to " + IDP_JS_TIMEOUT_SEC + " seconds.",
                        ex);
            } else {
                throw new ScriptingException("Error during post-processing script execution", ex);
            }
        } catch (IOException ex) {
            throw new ElDocRuntimeException("Error preparing postprocessor script for execution", ex);
        }
    }


    /**
     * Checks, if CustomField validation status changed after post-processing. For tables, validation status
     * is considered changed, if it is changed at least for one cell
     *
     * @param cf Original {@link CustomField}, before post-processing
     * @param newCf {@link PostProcessorCustomField} with new values (result of PostProcessing execution)
     * @return {@code true}, if field validation status is changed after post-processing
     */
    private boolean isCfValidationStatusChanged(CustomField cf, PostProcessorCustomField newCf) {
        boolean changed = false;
        switch (cf.getType()) {
            case CustomField.TYPE_RECO_TABLE ->  {
                List<List<List<Integer>>> tableConfOld = cf.getRecoProp().getConfidenceTable();
                List<List<Boolean>> tableValidationStatusNew = ((List<List<Boolean>>) newCf.getValidated());
                for (int rowIdx = 0; rowIdx < Math.min(tableConfOld.size(), tableValidationStatusNew.size()); rowIdx++) {
                    List<List<Integer>> rowConfOld = tableConfOld.get(rowIdx);
                    List<Boolean> eowValidationStatusNew = tableValidationStatusNew.get(rowIdx);
                    for (int cellIdx = 0; cellIdx < Math.min(rowConfOld.size(), eowValidationStatusNew.size()); cellIdx++) {
                        List<Integer> cellConfOld = rowConfOld.get(cellIdx);
                        boolean cellValidatedOld = cellConfOld.stream().mapToInt(i -> i).min().orElse(0) >= cf.getRecoProp().getConfThreshold();
                        boolean cellValidatedNew = eowValidationStatusNew.get(cellIdx);
                        changed = changed || cellValidatedOld != cellValidatedNew;
                        if (changed) {
                            break;
                        }
                    }
                    if (changed) {
                        break;
                    }
                }
            }
            default -> {
                boolean newCfValidationStatus = (boolean) newCf.getValidated();
                boolean oldCfValidationStatus = !RecognitionService.isFieldRequiresValidation(cf);
                changed = changed || newCfValidationStatus != oldCfValidationStatus;
            }
        }
        return changed;
    }


    /**
     * Extracts field confidence levels from {@link PostProcessorCustomField}, regarding field's 'validated' flag, and updates corresponding
     * CustomField properties
     *
     * @param customField {@link CustomField} to be updated
     * @param newCfResult {@link PostProcessorCustomField} with new values (result of PostProcessing execution)
     * @param recoValue Old recognition value
     * @param newRecoValue New recognition value
     * @param lang Language to be used for CustomField conversion
     * @return {@code true}, if field is validated after post-processing, or {@code false} otherwise
     */
    private boolean updateCustomFieldConfidence(CustomField customField, PostProcessorCustomField newCfResult, Object recoValue, Object newRecoValue, String lang) {
        boolean newValidatedState = true;
        CustomField newCustomField = cfFromPostProcCf(newCfResult, lang);
        if (CustomField.TYPE_RECO_TABLE.equals(customField.getType())) {
            List<List<String>> tableValue = ((List<List<String>>) recoValue);
            List<List<List<Integer>>> tableConf = customField.getRecoProp().getConfidenceTable();
            List<List<String>> newTableValue = ((List<List<String>>) newRecoValue);

            // Ensure table has proper sizes
            // Update rows count
            if (newTableValue.size() < tableValue.size()) {
                tableValue.subList(newTableValue.size(), tableValue.size()).clear();
                tableConf.subList(newTableValue.size(), tableConf.size()).clear();
            } else if (newTableValue.size() > tableValue.size()) {
                for (int rowIdx = tableValue.size(); rowIdx < newTableValue.size(); rowIdx++) {
                    List<String> row = new ArrayList<>();
                    List<List<Integer>> rowConf = new ArrayList<>();
                    for (int cellIdx = 0; cellIdx < customField.getFields().size(); cellIdx++) {
                        row.add("");
                        List<Integer> cellConf = new ArrayList<>();
                        int newConf = ((List<List<Boolean>>) newCfResult.getValidated()).get(rowIdx).get(cellIdx) ? FIELD_VALIDATED_CONF : FIELD_NOT_VALIDATED_CONF;
                        for (int symbIdx = 0; symbIdx < newTableValue.get(rowIdx).get(cellIdx).length(); symbIdx++) {
                            cellConf.add(newConf);
                        }
                        rowConf.add(cellConf);
                    }
                    tableValue.add(row);
                    tableConf.add(rowConf);
                }
            }

            // Update columns count
            for (int i = 0; i < newTableValue.size(); i++) {
                List<String> row = newTableValue.get(i);
                // if new row size is smaller, than already existing - reset confidence levels of missing columns
                if (row.size() < ((List<List<String>>) recoValue).get(i).size()) {
                    for (int j = row.size(); j < ((List<List<String>>) recoValue).get(i).size(); j++) {
                        customField.getRecoProp().getConfidenceTable().get(i).set(j, new ArrayList<>(Arrays.asList(FIELD_VALIDATED_CONF)));
                    }
                }

                // If new row size is greater, than already existing - remove exceeding values
                if (row.size() > ((List<List<String>>) recoValue).get(i).size()) {
                    row.subList(((List<List<String>>) recoValue).get(i).size(), row.size()).clear();
                }
            }

            // Update cell values and confidences
            for (int rowIdx = 0; rowIdx < newTableValue.size(); rowIdx++) {
                for (int cellIdx = 0; cellIdx < newTableValue.get(rowIdx).size(); cellIdx++) {
                    String newCellValue = newTableValue.get(rowIdx).get(cellIdx);
                    String oldCellValue = tableValue.get(rowIdx).get(cellIdx);
                    boolean newValidatedStatus = ((List<List<Boolean>>) newCfResult.getValidated()).get(rowIdx).get(cellIdx);
                    boolean oldCfValidationStatus = !(tableConf.get(rowIdx).get(cellIdx).stream().mapToInt(Integer::intValue).min().orElse(100) < customField.getFields().get(cellIdx).getRecoProp().getConfThreshold());
                    if (!Objects.equals(newCellValue, oldCellValue) || newValidatedStatus != oldCfValidationStatus) {
                        int newConf;
                        if (newValidatedStatus != oldCfValidationStatus || isBlank(oldCellValue)) {
                            newConf = newCustomField.getRecoProp().getConfidenceTable().get(rowIdx).get(cellIdx).get(0);
                        } else {
                            newConf = tableConf.get(rowIdx).get(cellIdx).stream().mapToInt(Integer::intValue).min().orElse(0);
                        }
                        List<Integer> cellConf = new ArrayList<>();
                        for (int symbIdx = 0; symbIdx < newCellValue.length(); symbIdx++) {
                            cellConf.add(newConf);
                        }
                        tableValue.get(rowIdx).set(cellIdx, newCellValue);
                        tableConf.get(rowIdx).set(cellIdx, cellConf);
                        newValidatedState = newValidatedState && newValidatedStatus;
                    }
                }
            }
        } else if (CustomField.TYPE_RECO_OMR.equals(customField.getType())) {
            boolean newCfValidationStatus = (boolean) newCfResult.getValidated();
            boolean oldCfValidationStatus = !RecognitionService.isFieldRequiresValidation(customField);

            // If some marks were removed during post-processing - fill list with enough marks at the end with confidence level 99 and value 'false'
            if (((List<Boolean>) newRecoValue).size() < ((List<Boolean>) recoValue).size()) {
                for (int i = ((List<Boolean>) newRecoValue).size(); i < ((List<Boolean>) recoValue).size(); i++) {
                    ((List<Boolean>) recoValue).set(i, false);
                    ((List<Boolean>) newRecoValue).add(false);
                    customField.getRecoProp().getConfidenceOmr().set(i, FIELD_VALIDATED_CONF);
                }
            }

            // If new OMR size is greater, than already existing - remove exceeding values
            if (((List<Boolean>) newRecoValue).size() > ((List<Boolean>) recoValue).size()) {
                ((List<Boolean>) newRecoValue).subList(((List<Boolean>) recoValue).size(), ((List<Boolean>) newRecoValue).size()).clear();
            }

            if (!Objects.equals(recoValue, newRecoValue) || newCfValidationStatus != oldCfValidationStatus) {
                int newConf;
                if (newCfValidationStatus != oldCfValidationStatus) {
                    newConf = newCustomField.getRecoProp().getConfidenceOmr().get(0);
                } else {
                    newConf = customField.getRecoProp().getConfidenceMin();
                }
                List<Integer> newOmrConf = new ArrayList<>(Collections.nCopies(((List<Boolean>) newRecoValue).size(), newConf));
                customField.getRecoProp().setConfidenceOmr(newOmrConf);
                newValidatedState = newValidatedState && newCfValidationStatus;
            }
        } else if (CustomField.TYPE_RECO.equals(customField.getType())) {
            boolean newCfValidationStatus = (boolean) newCfResult.getValidated();
            boolean oldCfValidationStatus = !RecognitionService.isFieldRequiresValidation(customField);
            if (!Objects.equals(recoValue, newRecoValue) || newCfValidationStatus != oldCfValidationStatus) {
                int newConf;
                if (newCfValidationStatus != oldCfValidationStatus || isBlank((String) recoValue)) {
                    newConf = newCustomField.getRecoProp().getConfidenceText().get(0);
                } else {
                    newConf = customField.getRecoProp().getConfidenceMin();
                }
                List<Integer> newTextConf = new ArrayList<>();
                for (int i = 0; i < ((String) newRecoValue).length(); i++) {
                    newTextConf.add(newConf);
                }
                customField.getRecoProp().setConfidenceText(newTextConf);
                newValidatedState = newValidatedState && newCfValidationStatus;
            }
        }

        return newValidatedState;
    }


    /**
     * Performs validation of the post-processing settings on the form
     *
     * @param postProcessorFunc
     * @param scriptScope
     * @param os
     * @return
     * @throws TimeoutException
     */
    public boolean validateRecoDataPostProcessor(String postProcessorFunc, Map<String, Object> scriptScope, OutputStream os) throws TimeoutException {

        // Create Graal.js script engine
        try (Context context = Context.newBuilder(SCRIPT_LANG)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> className.equals("ai.eldoc.reco.Bitap") || className.equals("ai.eldoc.reco.Bitap.BitapResult"))
                .option("engine.WarnInterpreterOnly", "false")
                .out(os)
                .build()) {
            Source source = Source.newBuilder(SCRIPT_LANG, "function test(" + VALUE_VAR_NAME + ") {" + System.lineSeparator()
                    + postProcessorFunc + System.lineSeparator()
                    + "}", "postProcessor.js").build();
            context.getBindings(SCRIPT_LANG).putMember("scriptScope", JsonbUtils.getInstance().toJson(scriptScope));

            // Set timeout - forcibly close Graal.js context, if timeout reached
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    context.close(true);
                }
            }, IDP_JS_TIMEOUT_SEC * 1000);

            // Add predefined includes
            addCodeRepoIncludes(context);

            // Compile postprocessor
            context.eval(source);

            return true;
        } catch (PolyglotException ex) {
            if (ex.isCancelled()) {
                throw new TimeoutException("PostProcessing interrupted due to timeout."
                        + "Current timeout per field set to " + IDP_JS_TIMEOUT_SEC + " seconds.",
                        ex);
            } else {
                throw new ScriptingException("Error during post-processing script execution", ex);
            }
        } catch (IOException ex) {
            throw new ElDocRuntimeException("Error preparing postprocessor script for execution", ex);
        }
    }


    /**
     * Creates new RecoData JsonObject that keeps only data that should be passed to script
     *
     * @param recoData
     * @param lang
     * @param withFormatting
     * @return
     */
    public static String recoDataToJsonConverter(RecoData recoData, String lang, boolean withFormatting) {
        Map<String, Object> recoDataMap = new HashMap<>();
        List<PostProcessorCustomField> cFields = recoData.getCfDef().stream()
                .map(cf -> {
                    String cfDsName = recoData.getCfDsFieldName(cf);
                    Object value = recoData.getCfData().get(cfDsName);
                    return new PostProcessorCustomField(cf, value, lang);
                })
                .collect(Collectors.toList());
        recoDataMap.put(RECODATA_FIELD_DATA, cFields);
        recoDataMap.put(RecoData._VALIDATED, recoData.isValidated());
        recoDataMap.put(RecoData._PAGE_IDX_START, recoData.getPageIdxStart());
        recoDataMap.put(RecoData._PAGE_IDX_END, recoData.getPageIdxEnd());
        return JsonbUtils.getInstance(withFormatting).toJson(recoDataMap);
    }


    /**
     * Parses provided JSON string representation of RecoData to RecoData itself.
     * JSON representation should follow PostProcessor format
     *
     * @param recoDataJson
     * @param lang
     * @return
     */
    public static RecoData jsonToRecoDataConverter(String recoDataJson, String lang) {
        RecoData recoData = RecoData.fromJson("{}");
        Map<String, Object> recoDataMap = JsonbUtils.getInstance().fromJson(recoDataJson, new TypeLiteral<Map<String, Object>>() {
        }.getType());
        recoData.setValidated((boolean) recoDataMap.get(RecoData._VALIDATED));
        recoData.setPageIdxStart(((Number) recoDataMap.get(RecoData._PAGE_IDX_START)).intValue());
        recoData.setPageIdxEnd(((Number) recoDataMap.get(RecoData._PAGE_IDX_END)).intValue());

        List<CustomField> cfDef = new ArrayList<>();
        Map<String, Object> cfData = new HashMap<>();
        recoData.setCfDef(cfDef);
        recoData.setCfData(cfData);

        List<TreeMap<String, Object>> cFields = (List<TreeMap<String, Object>>) recoDataMap.get(RECODATA_FIELD_DATA);
        cFields.forEach(postProcCfMap -> {
            PostProcessorCustomField postProcCf = JsonbUtils.getInstance().fromJson(JsonbUtils.getInstance().toJson(postProcCfMap), PostProcessorCustomField.class);
            Object value = postProcCf.getValue();
            CustomField cf = cfFromPostProcCf(postProcCf, lang);
            cfDef.add(cf);
            cfData.put("cf" + cfDef.indexOf(cf), value);
        });
        return recoData;
    }


    /**
     * Converts {@link PostProcessorCustomField} to {@link CustomField}
     *
     * @param postProcCf
     * @param lang
     * @return
     */
    public static CustomField cfFromPostProcCf(PostProcessorCustomField postProcCf, String lang) {
        CustomField cf = new CustomField();
        cf.getName().put(lang, postProcCf.getName());
        cf.setType(postProcCf.getType());

        if (CustomField.TYPE_RECO_TABLE.equals(postProcCf.getType()) || CustomField.TYPE_RECO_OMR.equals(postProcCf.getType())) {
            List<CustomField> fields = postProcCf.getFields().stream()
                    .map(field -> cfFromPostProcCf(field, lang))
                    .collect(Collectors.toList());
            cf.setFields(fields);
        }

        if (CustomField.TYPE_RECO_TABLE.equals(postProcCf.getType())) {
            for (int i = 0; i < postProcCf.getFields().size(); i++) {
                cf.getFields().get(i).getRecoProp().setConfThreshold(((Number) postProcCf.getFields().get(i).getConfThreshold()).intValue());
            }
        } else {
            cf.getRecoProp().setConfThreshold(((Number) postProcCf.getConfThreshold()).intValue());
        }

        if (postProcCf.getValue() != null) {
            if (CustomField.TYPE_RECO_TABLE.equals(postProcCf.getType())) {
                List<List<List<Integer>>> newTableConf = new ArrayList<>();
                List<List<String>> newTableValues = (List<List<String>>) postProcCf.getValue();
                for (int rowIdx = 0; rowIdx < newTableValues.size(); rowIdx++) {
                    List<String> newRowValue = newTableValues.get(rowIdx);
                    if (newRowValue == null) {
                        throw new ScriptingException("Returned table value has a wrong structure");
                    }
                    List<List<Integer>> newRowConf = new ArrayList<>();
                    for (int cellIdx = 0; cellIdx < newRowValue.size(); cellIdx++) {
                        int newConf = ((List<List<Boolean>>) postProcCf.getValidated()).get(rowIdx).get(cellIdx) ? FIELD_VALIDATED_CONF : FIELD_NOT_VALIDATED_CONF;
                        List<Integer> newCellConf = new ArrayList<>(Collections.nCopies(newRowValue.get(cellIdx).length(), newConf));
                        if (newCellConf.isEmpty()) {
                            // No per-symbol confidence was loaded, meaning cell is empty - use 'newConf' as confidence level of empty cell
                            newCellConf.add(newConf);
                        }
                        newRowConf.add(newCellConf);
                    }
                    newTableConf.add(newRowConf);
                }
                cf.getRecoProp().setConfidenceTable(newTableConf);
            } else if (CustomField.TYPE_RECO_OMR.equals(postProcCf.getType())) {
                int newConf = ((boolean) postProcCf.getValidated()) ? FIELD_VALIDATED_CONF : FIELD_NOT_VALIDATED_CONF;
                List<Integer> newOmrConf = new ArrayList<>(Collections.nCopies(((List<Boolean>) postProcCf.getValue()).size(), newConf));
                if (newOmrConf.isEmpty()) {
                    // No OpMarks returned (should not happen) - use 'newConf' as confidence level of empty OMR field
                    newOmrConf.add(newConf);
                }
                cf.getRecoProp().setConfidenceOmr(newOmrConf);
            } else {
                int newConf = ((boolean) postProcCf.getValidated()) ? FIELD_VALIDATED_CONF : FIELD_NOT_VALIDATED_CONF;
                List<Integer> newTextConf = new ArrayList<>(Collections.nCopies(((String) postProcCf.getValue()).length(), newConf));
                if (newTextConf.isEmpty()) {
                    // No per-symbol value returned - use 'newConf' as confidence level of empty field
                    newTextConf.add(newConf);
                }
                cf.getRecoProp().setConfidenceText(newTextConf);
            }
        }

        cf.setTags(postProcCf.getTags());

        return cf;
    }


    /**
     * Wraps CustomField value, preparing it for use in Post-Processing function.<br>
     * If provided value is null - return default value for provided field type:
     * <ul>
     * <li>{@link CustomField#TYPE_RECO} - default value is empty String;</li>
     * <li>{@link CustomField#TYPE_RECO_TABLE} or {@link CustomField#TYPE_RECO_OMR} - default value is empty Array;</li>
     * </ul>
     * This allows to use value in script without checks for null
     *
     * @param fieldType
     * @param value
     * @return
     */
    private static Object getWrappedOrDefault(CustomField cf, Object value) {
        String fieldType = cf.getType();
        if (CustomField.TYPE_RECO.equals(fieldType)) {
            return getWrappedOrDefaultBasicValue(value);
        } else if (CustomField.TYPE_RECO_TABLE.equals(fieldType)) {
            return getWrappedOrDefaultTableValue(cf, value);
        } else if (CustomField.TYPE_RECO_OMR.equals(fieldType)) {
            return getWrappedOrDefaultOmrValue(value);
        } else {
            return getWrappedOrDefaultBasicValue(value);
        }
    }


    /**
     * Wraps TABLE-type field value to avoid nulls. If value is null, then empty array is returned.
     * Otherwise, each table cell processed separately to properly handle nulls in all column types.<br>
     * <b>IMPORTANT:</b> this method creates copy of original field's value and works with copy, to
     * avoid changes to original RecoFieldResult.
     *
     * @param cf
     * @param value
     * @return
     */
    static Object getWrappedOrDefaultTableValue(CustomField cf, Object value) {
        if (value == null) {
            return new ArrayList<>();
        }

        List<List<Object>> newValue = new ArrayList<>();
        // Copy all values to avoid modification of source collection, and wrap them
        for (List<Object> row : (List<List<Object>>) value) {
            List<Object> newRow = new ArrayList<>();
            for (int cellIdx = 0; cellIdx < row.size(); cellIdx++) {
                Object cellValue = row.get(cellIdx);
                CustomField colDefField = cf.getFields().get(cellIdx);
                newRow.add(getWrappedOrDefault(colDefField, cellValue));
            }
            newValue.add(newRow);
        }
        return newValue;
    }


    /**
     * Wraps TEXT-type field value to avoid nulls. Also used to handle other field types, that doesn't require
     * type-specific handling logic.<br>
     * NULL values are replaced with empty string.
     *
     * @param value
     * @return
     */
    static Object getWrappedOrDefaultBasicValue(Object value) {
        if (value == null) {
            return "";
        }
        return value;
    }


    /**
     * Wraps OMR-type field value to avoid nulls.<br>
     * <ul>
     * <li>If value is null, then returns empty list. This case should never happen, as 'NOT FOUND' OMR fields
     * get default values (false) for each defined optical mark, so in any situation it should be non-empty list.
     * This check done to guarantee, that there is no need to check for null/undefined in post-processing code;</li>
     * <li>If value is not null - returns value itself. No need to perform additional check for values of each
     * optical mark, as they can only be true or false, even if they was not located;</li>
     * </ul>
     *
     * @param value
     * @return
     */
    static Object getWrappedOrDefaultOmrValue(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        return value;
    }


    /**
     * Adds predefined code includes to provided context
     *
     * @param context
     * @throws IOException
     */
    private void addCodeRepoIncludes(Context context) throws IOException {
        // Add predefined includes
        List<URL> includeScriptList = getCodeRepoIncludes();
        for (URL sourceUrl : includeScriptList) {
            Source includeSource = Source.newBuilder(SCRIPT_LANG, sourceUrl).build();
            context.eval(includeSource);
        }
    }


    /**
     * Returns list of URLs for predefined code includes in post-processing
     *
     * @return
     */
    private List<URL> getCodeRepoIncludes() {
        List<URL> filesList = new ArrayList<>();
        URL bitapScriptPath = RecoPostProcessorService.class.getClassLoader().getResource(SCRIPT_BASE_DIR + "/Bitap.js");
        filesList.add(bitapScriptPath);
        return filesList;
    }


    public static class PostProcessorCustomField {

        private String name;
        private String type;
        private List<PostProcessorCustomField> fields;
        private Object value;
        private Object conf;
        private Object confThreshold;
        private Object validated;
        private Set<String> tags;


        public PostProcessorCustomField() {
        }


        public PostProcessorCustomField(CustomField cf, Object cfValue, String lang) {
            name = cf.getName().get(lang);
            type = cf.getType();
            if (CustomField.TYPE_RECO_TABLE.equals(cf.getType()) || CustomField.TYPE_RECO_OMR.equals(cf.getType())) {
                fields = cf.getFields().stream()
                        .map(field -> new PostProcessorCustomField(field, null, lang))
                        .collect(Collectors.toList());
            }
            value = getWrappedOrDefault(cf, cfValue);
            tags = new HashSet<>(cf.getTags());
            tags.remove(CustomField.TAG_RECO_FIELD);

            // Fill confidence levels
            if (CustomField.TYPE_RECO.equals(cf.getType())) {
                conf = cf.getRecoProp().getConfidenceMin();
            } else if (CustomField.TYPE_RECO_TABLE.equals(cf.getType())) {
                conf = cf.getRecoProp().getConfidenceTable().stream()
                        .map(row -> {
                            return row.stream()
                                    .map(cell -> cell.stream()
                                    .mapToInt(Integer::intValue)
                                    .min()
                                    .orElse(0))
                                    .collect(Collectors.toList());
                        })
                        .collect(Collectors.toList());
            } else if (CustomField.TYPE_RECO_OMR.equals(cf.getType())) {
                conf = cf.getRecoProp().getConfidenceOmr();
            }

            // Fill 'Validated' flags
            if (CustomField.TYPE_RECO_TABLE.equals(cf.getType())) {
                List<List<Boolean>> tableValidated = new ArrayList<>();

                // Fill validation result with initial (TRUE) values for each column
                for (int i = 0; i < ((List<List<String>>) cfValue).size(); i++) {
                    List<Boolean> rowValidated = new ArrayList<>(Collections.nCopies(((List<List<String>>) cfValue).get(i).size(), true));
                    tableValidated.add(rowValidated);
                }

                // Check each cell of the table, if it is requires validation
                for (AtomicInteger i = new AtomicInteger(0); i.get() < cf.getFields().size(); i.incrementAndGet()) {
                    CustomField column = cf.getFields().get(i.get());
                    for (int j = 0; j < cf.getRecoProp().getConfidenceTable().size(); j++) {
                        List<Integer> cellConfidence = cf.getRecoProp().getConfidenceTable().get(j).get(i.get());
                        String cellValue = ((List<List<String>>) cfValue).get(j).get(i.get());
                        boolean isValidated = true;
                        if (CustomField.TYPE_RECO.equals(column.getType()) && isBlank(cellValue)) {
                            // Cells with empty value are considered autovalidated regardless of confidence threshold set for column
                            continue;
                        }
                        if (cellConfidence.stream().mapToInt(Integer::intValue).min().orElse(100) < column.getRecoProp().getConfThreshold()) {
                            isValidated = false;
                        }
                        tableValidated.get(j).set(i.get(), isValidated);
                    }
                }
                validated = tableValidated;
            } else {
                validated = !RecognitionService.isFieldRequiresValidation(cf);
            }
            if (!CustomField.TYPE_RECO_TABLE.equals(cf.getType())) {
                confThreshold = cf.getRecoProp().getConfThreshold();
            }
        }


        public String getName() {
            return name;
        }


        public void setName(String name) {
            this.name = name;
        }


        public String getType() {
            return type;
        }


        public void setType(String type) {
            this.type = type;
        }


        public List<PostProcessorCustomField> getFields() {
            return fields;
        }


        public void setFields(List<PostProcessorCustomField> fields) {
            this.fields = fields;
        }


        public Object getValue() {
            return value;
        }


        public void setValue(Object value) {
            this.value = value;
        }


        public Object getConf() {
            return conf;
        }


        public void setConf(Object conf) {
            this.conf = conf;
        }


        public Object getConfThreshold() {
            return confThreshold;
        }


        public void setConfThreshold(Object confThreshold) {
            this.confThreshold = confThreshold;
        }


        public Object getValidated() {
            return validated;
        }


        public void setValidated(Object validated) {
            this.validated = validated;
        }


        public Set<String> getTags() {
            return tags;
        }


        public void setTags(Set<String> tags) {
            this.tags = tags;
        }


        @Override
        public int hashCode() {
            int hash = 3;
            hash = 17 * hash + Objects.hashCode(this.name);
            hash = 17 * hash + Objects.hashCode(this.value);
            hash = 17 * hash + Objects.hashCode(this.conf);
            hash = 17 * hash + Objects.hashCode(this.confThreshold);
            hash = 17 * hash + Objects.hashCode(this.validated);
            hash = 17 * hash + Objects.hashCode(this.tags);
            return hash;
        }


        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final PostProcessorCustomField other = (PostProcessorCustomField) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.value, other.value)) {
                return false;
            }
            if (!Objects.equals(this.conf, other.conf)) {
                return false;
            }
            if (!Objects.equals(this.confThreshold, other.confThreshold)) {
                return false;
            }
            if (!Objects.equals(this.validated, other.validated)) {
                return false;
            }
            return Objects.equals(this.tags, other.tags);
        }

    }

}
