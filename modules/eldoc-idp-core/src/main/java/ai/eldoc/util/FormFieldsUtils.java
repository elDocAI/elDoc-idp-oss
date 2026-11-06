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
package ai.eldoc.util;

import ai.eldoc.data.DataType;
import ai.eldoc.model.CustomField;
import ai.eldoc.model.CustomFieldAwareModel;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Form Fields Utils class<br>
 * Provides utility methods for converting form field values to human-readable form with applied formatting, where applicable
 */
@ApplicationScoped
public class FormFieldsUtils implements Serializable {

    @Serial
    private static final long serialVersionUID = -7226745516021206493L;

    private static final Logger LOG = LoggerFactory.getLogger(FormFieldsUtils.class);

    private static final String CF_LINK_PATTERN_STR = "\\[(?<linkText>.*)\\]\\((?<url>.+)\\)";
    private static final Pattern CF_LINK_PATTERN = Pattern.compile(CF_LINK_PATTERN_STR);


    //
    // Class methods ------------------------------------------------------------------------------
    //


    /**
     * Returns prepared for UI (CSV Export, Templates replacement, etc) values of Workflow Standard Fields
     *
     * @param value
     * @param dataType
     * @param dateFormat
     * @param timeFormat
     * @param timeZone
     * @return
     */
    public String getFieldData(Object value, int dataType, String dateFormat, String timeFormat, String timeZone) {
        try {
            switch (dataType) {
                case DataType.DATE:
                    return formatDateTime(value, dateFormat, timeFormat, timeZone);
                case DataType.NUMBER:
                case DataType.STRING:
                default:
                    // this is a default case - to treat field as String
                    return value != null ? String.valueOf(value) : "";
            }
        } catch (Exception e) {
            LOG.error("Error converting value '{}' to dataType: {}", value, dataType, e);
        }
        return "-err-";
    }


    private static String formatDateTime(Object value, String dateFormat, String timeFormat, String timeZone) {
        String formatString = dateFormat + (timeFormat == null ? "" : (" " + timeFormat));
        SimpleDateFormat formatter = new SimpleDateFormat(formatString);
        formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
        return (value instanceof Date date) ? formatter.format(date) : "";
    }


    /**
     * Returns prepared for UI (CSV Export, Templates replacement, etc) values of Workflow Custom Fields
     *
     * @param workflow
     * @param fieldName
     * @param fieldType
     * @param lang
     * @param dateFormat
     * @param timeFormat
     * @param timeZone
     * @return
     */
    public String getCustomFieldData(CustomFieldAwareModel workflow, String fieldName, String fieldType, String lang, String dateFormat, String timeFormat, String timeZone) {
        try {
            if (null == fieldType) {
                // basically this should never happen
                return "";
            }
            Object fieldValue = workflow.getCfData().get(fieldName);

            return getFormattedValue(fieldValue, fieldType, lang, dateFormat, timeFormat, timeZone);
        } catch (Exception e) {
            LOG.error(null, e);
        }
        return "-err-";
    }


    /**
     * Returns field value, formatted in human-readable form according to field type and provided datetime format (if applicable)
     *
     * @param fieldValue
     * @param fieldType
     * @param lang
     * @param dateFormat
     * @param timeFormat
     * @param timeZone
     * @return
     */
    public String getFormattedValue(Object fieldValue, String fieldType, String lang, String dateFormat, String timeFormat, String timeZone) {
        switch (fieldType) {
            case CustomField.TYPE_CHECKBOX:
                return fieldValue != null ? fieldValue.toString() : "";

            case CustomField.TYPE_DATE:
                return formatDateTime(fieldValue, dateFormat, null, timeZone);

            case CustomField.TYPE_DATETIME:
                return formatDateTime(fieldValue, dateFormat, timeFormat, timeZone);

            case CustomField.TYPE_NUMBER_INTEGER:
                return (fieldValue != null && (fieldValue instanceof Integer || fieldValue instanceof Long)) ? fieldValue.toString() : "";

            case CustomField.TYPE_NUMBER_FRACTIONAL:
                Double value = null;
                if (fieldValue instanceof Double doubleValue) {
                    value = doubleValue;
                } else if (fieldValue instanceof Number numberValue) {
                    value = numberValue.doubleValue();
                }
                return value != null ? String.format("%.2f", value) : "";
            //return fieldValue != null ? String.valueOf(fieldValue) : "";

            case CustomField.TYPE_STRING: // this is the default behaviour, covered by the "default:" case
            default:
                // also covers CustomField.TYPE_STRING
                return fieldValue != null ? String.valueOf(fieldValue) : "";
        }
    }


    public static Pair<String, String> parseCfLink(String cfValue) {
        Matcher matcher = CF_LINK_PATTERN.matcher(cfValue);
        if (matcher.matches()) {
            return Pair.of(matcher.group("linkText"), matcher.group("url"));
        }

        return null;
    }


    public static String htmlToPlainText(String html) {
        Element body = Jsoup.parseBodyFragment(html).body();
        StringBuilder result = new StringBuilder();

        appendText(body, result);

        return result.toString().trim();
    }


    private static void appendText(Node node, StringBuilder result) {
        if (node instanceof TextNode textNode) {
            result.append(textNode.getWholeText());
            return;
        }

        if (!(node instanceof Element element)) {
            return;
        }

        String tag = element.normalName();

        if ("br".equals(tag)) {
            // A sole <br> inside a paragraph is an editor placeholder.
            // The paragraph itself will add the required newline.
            if (!isSoleParagraphPlaceholder(element)) {
                result.append(System.lineSeparator());
            }
            return;
        }

        for (Node child : element.childNodes()) {
            appendText(child, result);
        }

        if ("p".equals(tag)) {
            result.append(System.lineSeparator());
        }
    }


    private static boolean isSoleParagraphPlaceholder(Element br) {
        Element parent = br.parent();

        if (parent == null || !"p".equals(parent.normalName())) {
            return false;
        }

        return parent.childrenSize() == 1
                && parent.child(0) == br
                && parent.ownText().isBlank();
    }

}
