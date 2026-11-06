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
package ai.eldoc.ui.component;

import ai.eldoc.util.*;
import jakarta.faces.component.*;
import jakarta.faces.context.FacesContext;
import org.apache.commons.lang3.tuple.Pair;
import org.primefaces.component.link.Link;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * ListValue Label backing component class
 */
@FacesComponent("cfLink")
public class CfLink extends UIOutput implements NamingContainer {

    private static final String ATTR_DS = "dataSource";
    private static final String ATTR_FIELD_NAME = "fieldName";
    private static final String ATTR_LABEL = "label";

    private Link _link;
    private UIOutput _outputLabel;
    private String _linkText;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns the component family of {@link UINamingContainer}. (that's just required by a composite component)
     *
     * @return
     */
    @Override
    public String getFamily() {
        return UINamingContainer.COMPONENT_FAMILY;
    }


    /**
     * Set the available values
     *
     * @param context
     * @throws java.io.IOException
     */
    @Override
    public void encodeBegin(FacesContext context) throws IOException {
        Map<String, Object> dataSource = (Map<String, Object>) getAttributes().get(ATTR_DS);
        String fieldName = (String) getAttributes().get(ATTR_FIELD_NAME);
        String label = (String) getAttributes().get(ATTR_LABEL);

        Object customFieldValue = dataSource.get(fieldName);
        String linkMd = customFieldValue == null ? null : customFieldValue.toString();

        _linkText = getCfLinkText(linkMd, JsfUtils.getLocale(context));
        String linkTitle = label + ": " + _linkText;

        String linkUrl = getCfLinkUrl(linkMd);

        _outputLabel.setValue(label);
        _link.setTitle(linkTitle);
        _link.setHref(linkUrl);

        super.encodeBegin(context);
    }


    private String getCfLinkText(String cfValue, Locale locale) {
        Pair<String, String> link = FormFieldsUtils.parseCfLink(cfValue);
        if (link == null) {
            return null;
        }
        if (isBlank(link.getLeft())) {
            return LabelUtils.getLabel("general.link.textDefault", locale);
        }
        return link.getLeft();
    }


    private String getCfLinkUrl(String cfValue) {
        Pair<String, String> link = FormFieldsUtils.parseCfLink(cfValue);
        if (link == null) {
            return null;
        }
        return link.getRight();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public Link getLink() {
        return _link;
    }


    public void setLink(Link link) {
        _link = link;
    }


    public UIOutput getOutputLabel() {
        return _outputLabel;
    }


    public void setOutputLabel(UIOutput outputLabel) {
        _outputLabel = outputLabel;
    }


    public String getLinkText() {
        return _linkText;
    }


    public void setLinkText(String linkText) {
        _linkText = linkText;
    }

}
