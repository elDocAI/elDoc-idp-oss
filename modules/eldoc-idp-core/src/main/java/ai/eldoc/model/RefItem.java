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

package ai.eldoc.model;

import ai.eldoc.constants.Pages;
import ai.eldoc.util.I18n;
import ai.eldoc.util.LabelUtils;
import java.io.Serial;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Reference Item Model
 */
public class RefItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 921847314657292930L;

    private final String _id;
    private final Locale _locale;
    private final TYPE _type;

    private String _value;
    private String _title;
    private String _text;
    private String _target = "_blank";
    private String _style;


    public enum TYPE {
        WORKFLOW, CRM_ACTION
    };


    public RefItem(TYPE type, String id, Workflow workflow, Locale locale) {
        _type = type;
        _id = id;
        _locale = locale;
        initAsWorkflow(workflow);
    }


    private void initAsWorkflow(Workflow workflow) {
        if (workflow == null) {
            initAsNotFound(TYPE.WORKFLOW, _id);
        } else {
            String docNum = Objects.requireNonNullElse(workflow.getNum(), "");
            String docTitle = Objects.requireNonNullElse(workflow.getNameByLang(I18n.getLangSysCodeByLocale(_locale)), "");
            String docTopic = Objects.requireNonNullElse(workflow.getTopic(), "");
            String text = new StringBuilder()
                    .append(docNum)
                    .append(" ")
                    .append(docTitle)
                    .append(": ")
                    .append(docTopic)
                    .append(" [")
                    .append(workflow.getIdWeb())
                    .append("]")
                    .toString();
            _text = (text.length() <= 64 ? text : text.substring(0, 60).trim() + "...");
            _value = Pages.PAGE_WORKFLOW + "?id=" + workflow.getIdWeb();
            _title = docTitle.trim() + ": " + docTopic.trim();
        }
    }


    private void initAsNotFound(TYPE type, String id) {
        _title = isBlank(_title) ? MessageFormat.format(LabelUtils.getLabel("ccRefDocItem.notFoundMsg", _locale), id) : _title;
        _text = LabelUtils.getLabel("general.search.docNotFound", _locale);
        _style = "color:red";
        if (Objects.requireNonNull(type) == TYPE.WORKFLOW) {
            _value = Pages.PAGE_WORKFLOW + "?id=" + id;
        }
    }


    public String getValue() {
        return _value;
    }


    public String getTitle() {
        return _title;
    }


    public String getText() {
        return _text;
    }


    public String getTarget() {
        return _target;
    }


    public String getStyle() {
        return _style;
    }
}
