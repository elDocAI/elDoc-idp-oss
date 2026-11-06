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

import ai.eldoc.exceptions.TimeoutException;
import ai.eldoc.model.CustomField;
import ai.eldoc.model.Workflow;
import ai.eldoc.util.JsonbUtils;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.Strings;
import org.graalvm.polyglot.*;
import java.beans.PropertyDescriptor;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import static ai.eldoc.service.CodeSnippetService.JS_TIMEOUT_SEC;
import static ai.eldoc.service.CodeSnippetService.SCRIPT_LANG;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Workflow Scripting Service
 */
@ApplicationScoped
public class WorkflowScriptingService {

    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Workflow post-create hook
     *
     * @param workflow
     * @param postCreateHookJSCode
     * @throws TimeoutException
     * @throws Exception
     */
    public void executePostCreateHook(Workflow workflow, String postCreateHookJSCode) throws TimeoutException, Exception {
        String SCOPE_NAME = "scope";
        String FUNCTION_NAME = "postCreateHook";
        String VAR_NAME = "workflow";

        Map<String, Object> scriptScope = new HashMap<>();

        String script = "var " + SCOPE_NAME + " = JSON.parse(scriptScope);"
                + "function " + FUNCTION_NAME + " (" + VAR_NAME + ") {"
                + VAR_NAME + " = JSON.parse(" + VAR_NAME + "); " + System.lineSeparator()
                + postCreateHookJSCode + System.lineSeparator()
                + " }";

        // Create Graal.js script engine
        try (OutputStream out = new ByteArrayOutputStream(); Context context = Context.newBuilder(SCRIPT_LANG)
                .allowHostAccess(HostAccess.ALL)
                .option("engine.WarnInterpreterOnly", "false")
                .out(out)
                .build()) {
            Source source = Source.newBuilder(SCRIPT_LANG, script, "postCreateHook.js").build();
            context.getBindings(SCRIPT_LANG).putMember("scriptScope", JsonbUtils.getInstance().toJson(scriptScope));

            // Set timeout - forcibly close Graal.js context, if timeout reached
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    context.close(true);
                }
            }, JS_TIMEOUT_SEC * 1000L);

            // Prepare input data
            Map<String, Object> workflowScriptInput = new HashMap<>();
            workflowScriptInput.put("_cfDef", workflow.getCfDef());   // List of CustomField definitions
            workflowScriptInput.put("_cfData", workflow.getCfData()); // Map of CustomFields values
            String workflowScriptInputJsonStr = JsonbUtils.getInstance().toJson(workflowScriptInput);

            // Compile postCreateHook code
            context.eval(source);

            // Execute postCreateHook code
            Value scriptResult = context.getBindings(SCRIPT_LANG).getMember(FUNCTION_NAME).execute(workflowScriptInputJsonStr);
            Map<String, Object> newScope = context.getBindings(SCRIPT_LANG).getMember(SCOPE_NAME).as(new TypeLiteral<Map<String, Object>>() {
            });
            scriptScope.clear();
            scriptScope.putAll(newScope);

            if (scriptResult.isNull() || !scriptResult.hasMembers()) {
                // Script returned no value - keep original value
                return;
            }

            // Process CFDef if present
            Value cfDefOutput = scriptResult.getMember("_cfDef");
            if (cfDefOutput != null && cfDefOutput.hasArrayElements()) {
                for (int cfIdx = 0; cfIdx < workflow.getCfDef().size(); cfIdx++) {
                    Value cfOutputValue = cfDefOutput.getArrayElement(cfIdx);
                    CustomField cfOnWorkflow = workflow.getCfDef().get(cfIdx);
                    // Read script result value to CustomField - required, as for Sync operation we need to pass 2 objects of same type
                    CustomField cfScriptResult = valueToObject(cfOutputValue, context, CustomField.class);
                    // Transfer white-listed values from editedCF (script output) to workflow cf (original object)
                    sync(cfOnWorkflow, cfScriptResult, "", List.of("properties", "enabled", "required", "fields.properties", "fields.enabled", "fields.required"));
                }
            }

            // Process CFData if present
            Value cfDataOutput = scriptResult.getMember("_cfData");
            if (cfDataOutput != null) {
                Map<String, Object> cfData = workflow.getCfData();
                for (CustomField cf : workflow.getCfDef()) {
                    String cfName = workflow.getCfDsFieldName(cf);
                    if (cfDataOutput.hasMember(cfName)) {
                        cfData.put(cfName, readObject(cfDataOutput.getMember(cfName)));
                    }
                }
            }
        } catch (PolyglotException ex) {
            if (ex.isCancelled()) {
                throw new TimeoutException("Workflow Post-Create Hook interrupted due to timeout (" + JS_TIMEOUT_SEC + "sec)", ex);
            } else {
                throw ex;
            }
        }
    }


    //
    // Private Methods ----------------------------------------------------------------------------
    //
    /**
     * Reads GraalVM Value into an object of required type
     *
     * @param <T>
     * @param value
     * @param context
     * @param targetType
     * @return
     * @throws IOException
     */
    private <T> T valueToObject(Value value, Context context, Class<T> targetType) throws IOException {
        String stringifyFunc = "function stringify(value) {return JSON.stringify(value);}";
        // Stringify return value
        Source stringifySrc = Source.newBuilder(SCRIPT_LANG, stringifyFunc, "stringify.js").build();
        context.eval(stringifySrc);
        Value cfJsonValue = context.getBindings(SCRIPT_LANG).getMember("stringify").execute(value);
        return JsonbUtils.getInstance().fromJson(cfJsonValue.asString(), targetType);
    }


    /**
     * Reads values to Object, basically native Java object (string/int/float/etc) or array of such elements (recursively, any nesting level);
     * IMPORTANT: Currently supports only Arrays (reading as List), Maps(as HashMaps) and native Java objects;
     *
     * @param value
     * @return
     */
    private Object readObject(Value value) {
        if (value.isNull()) {
            return null;
        }
        if (value.isString()) {
            // Java native object, i.e. String
            return value.asString();
        }
        if (value.hasArrayElements()) {
            List<Object> array = new ArrayList<>();
            for (int elementIndex = 0; elementIndex < value.getArraySize(); elementIndex++) {
                array.add(readObject(value.getArrayElement(elementIndex)));
            }
            return array;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new HashMap<>();
            for (String member : value.getMemberKeys()) {
                map.put(member, readObject(value.getMember(member)));
            }
            return map;
        }
        return null;
    }


    /**
     * Transfers values from Source object to Target object. Synchronization performed only for fields,
     * that are referenced in allowedPaths list
     *
     * @param <T>
     * @param target
     * @param source
     * @param currentPath
     * @param allowedPaths
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     */
    private <T> void sync(T target, T source, String currentPath, List<String> allowedPaths) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        PropertyDescriptor[] sourceObjectFields = PropertyUtils.getPropertyDescriptors(target);
        for (PropertyDescriptor field : sourceObjectFields) {
            String fieldName = field.getName();
            String fieldPath = currentPath + (isBlank(currentPath) ? "" : ".") + fieldName;
            List<String> matchingAllowedPaths = allowedPaths.stream()
                    .filter(allowedPath -> Strings.CI.startsWith(allowedPath, fieldPath))
                    .toList();
            if (matchingAllowedPaths.isEmpty()) {
                // Current field or any of its subFields is not within allowed paths list
                continue;
            }
            for (String allowedPath : matchingAllowedPaths) {
                if (Objects.equals(allowedPath, fieldPath) || field.getPropertyType() == Map.class) {
                    // TODO: Validate approach with maps
                    PropertyUtils.setSimpleProperty(target, fieldName, PropertyUtils.getSimpleProperty(source, fieldName));
                } else if (field.getPropertyType() == List.class) {
                    int childsCount = ((List<?>) PropertyUtils.getProperty(source, fieldName)).size();
                    for (int childIdx = 0; childIdx < childsCount; childIdx++) {
                        Object sourceArrayElement = PropertyUtils.getProperty(source, fieldName + "[" + childIdx + "]");
                        Object targetArrayElement = PropertyUtils.getProperty(target, fieldName + "[" + childIdx + "]");
                        sync(targetArrayElement, sourceArrayElement, fieldName, allowedPaths);
                    }
                }
            }
        }
    }
}
