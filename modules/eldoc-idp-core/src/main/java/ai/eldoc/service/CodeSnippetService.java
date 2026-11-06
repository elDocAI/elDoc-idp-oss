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

import ai.eldoc.dao.CodeSnippetDao;
import ai.eldoc.exceptions.ScriptingException;
import ai.eldoc.exceptions.TimeoutException;
import ai.eldoc.model.CodeSnippet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.management.ExecutionListener;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import static java.text.MessageFormat.format;


/**
 * CodeSnippet Service
 */
@ApplicationScoped
public class CodeSnippetService {

    public static final String ELDOC_JVM_OPTION_JS_TIMEOUT_SEC = "ai.eldoc.js.timeoutSec";
    public static final int JS_TIMEOUT_SEC = Integer.getInteger(ELDOC_JVM_OPTION_JS_TIMEOUT_SEC, 60);
    public static final String SCRIPT_LANG = "js";

    @Inject
    private CodeSnippetDao _codeSnippetDao;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public List<CodeSnippet> findByType(int type) {
        return _codeSnippetDao.findByType(type);
    }


    public void updateOrCreate(CodeSnippet codeSnippet) {
        _codeSnippetDao.updateOrCreate(null, codeSnippet);
    }


    public void delete(CodeSnippet codeSnippet) {
        _codeSnippetDao.delete(null, codeSnippet);
    }


    /**
     * Performs CodeSnippet validation.<br>
     * Validation rules:<br>
     * <ul>
     * <li>Script should have valid syntax;</li>
     * <li>Script should be evaluated within predefined timeout (see {@link #JS_TIMEOUT_SEC};</li>
     * <li>Script can only have functions and variables declaration on top level;</li>
     * </ul>
     *
     * @param codeSnippet
     * @throws TimeoutException if script execution was interrupted due to timeout
     * @throws ScriptingException If script has invalid syntax or violates validation rules
     */
    public void validate(CodeSnippet codeSnippet) throws TimeoutException, ScriptingException {
        try {
            validate(codeSnippet.getCodeDecoded());
        } catch (ParseException ex) {
            throw new ScriptingException(ex.getMessage());
        }
    }


    /**
     * Validates code snippet (by wrapping code in function)
     *
     * @param jsCodeSnippet
     * @throws TimeoutException
     * @throws ScriptingException
     */
    public void validateSnippet(String jsCodeSnippet) throws TimeoutException, ScriptingException {
        String jsCodeSnippetWrapped = "function jsCodeSnippet() {" + jsCodeSnippet + "}";
        validate(jsCodeSnippetWrapped);
    }


    /**
     * Performs JavaScript code validation.<br>
     * Validation rules:<br>
     * <ul>
     * <li>Script should have valid syntax;</li>
     * <li>Script should be evaluated within predefined timeout (see {@link #JS_TIMEOUT_SEC};</li>
     * <li>Script can only have functions and variables declaration on top level;</li>
     * </ul>
     *
     * @param jsCode
     * @throws TimeoutException if script execution was interrupted due to timeout
     * @throws ScriptingException If script has invalid syntax or violates validation rules
     */
    public void validate(String jsCode) throws TimeoutException, ScriptingException {
        try (Context context = Context.newBuilder(SCRIPT_LANG)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            Source source = Source.newBuilder(SCRIPT_LANG, jsCode, "script.js").build();

            ExecutionListener
                    .newBuilder()
                    .onEnter(e -> {
                        if (e.isRoot() && e.getRootName().equals(":program")) {
                            // This is root element of script code - do not validate it, as it's content will be processed next
                            return;
                        }

                        if (!e.getRootName().equals(":program")) {
                            // if root name is not ":program" - execution is inside the function, so this is valid node
                            return;
                        }

                        String code = e.getLocation().getCharacters().toString().trim();
                        if (code.startsWith("var ")) {
                            // This is variable declaration, so this is valid node
                            return;
                        }

                        // None of the rules above matched, so this is invalid node
                        throw new ScriptingException(format("Only function and variable declaration allowed in code lib. Line number: {0}. Invalid code: {1}", e.getLocation().getStartLine(), code));
                    })
                    .roots(true)
                    .statements(true)
                    .attach(context.getEngine());

            // Set timeout - forcibly close Graal.js context, if timeout reached
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    context.close(true);
                }
            }, JS_TIMEOUT_SEC * 1000);

            context.eval(source);
        } catch (PolyglotException ex) {
            if (ex.isCancelled()) {
                throw new TimeoutException("Script execution interrupted due to timeout (" + JS_TIMEOUT_SEC + " seconds)", ex);
            } else {
                throw new ScriptingException(ex.getMessage());
            }
        } catch (IOException ex) {
            throw new ScriptingException(ex.getMessage());
        }
    }

}
