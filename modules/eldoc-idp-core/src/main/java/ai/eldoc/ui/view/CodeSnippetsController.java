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
package ai.eldoc.ui.view;

import ai.eldoc.constants.Pages;
import ai.eldoc.context.ConfigContext;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.exceptions.ScriptingException;
import ai.eldoc.model.CodeSnippet;
import ai.eldoc.service.CodeSnippetService;
import ai.eldoc.util.JsfUtils;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.ViewScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.util.List;


/**
 * CodeSnippet Bean
 */
@Named
@ViewScoped
public class CodeSnippetsController extends AbstractUIController {

    @Serial
    private static final long serialVersionUID = 4366912515176867941L;

    private static final Logger LOG = LoggerFactory.getLogger(CodeSnippetsController.class);

    @Inject
    private CodeSnippetService _codeSnippetService;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private LocaleContext _localeContext;

    private CodeSnippet _codeSnippet;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @PostConstruct
    void init() {
        List<CodeSnippet> codeList = _codeSnippetService.findByType(CodeSnippet.TYPE_RECO);
        if (codeList.isEmpty()) {
            _codeSnippet = new CodeSnippet();
        } else {
            _codeSnippet = codeList.getFirst();
        }
    }


    public void btnSave() {
        try {
            _codeSnippetService.validate(_codeSnippet);
            _codeSnippetService.updateOrCreate(_codeSnippet);
            super.setModeRead();
        } catch (ScriptingException ex) {
            JsfUtils.addErrorMessage(ex.getMessage());
        } catch (Exception e) {
            LOG.error("Error saving CodeSnippet", e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public String btnCancel() {
        return JsfUtils.getNavigationTarget(Pages.PAGE_ADMIN_RECOCODEREPO);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public CodeSnippet getCodeSnippet() {
        return _codeSnippet;
    }


    public void setCodeSnippet(CodeSnippet codeSnippet) {
        _codeSnippet = codeSnippet;
    }

}
