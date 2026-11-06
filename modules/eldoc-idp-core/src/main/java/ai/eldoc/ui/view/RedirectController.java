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
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.annotation.RequestParameterMap;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Map;

/**
 * Redirect Controller
 */
@Named
@RequestScoped
public class RedirectController {

    @Inject
    @RequestParameterMap
    private Map<String, String> _requestParamMap;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public String redirectDocumentPageToWorkflowPage() {
        StringBuilder url = new StringBuilder(Pages.PAGE_WORKFLOW).append("?faces-redirect=true");

        _requestParamMap.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                url.append("&").append(key).append("=").append(value);
            }
        });

        return url.toString();
    }
}
