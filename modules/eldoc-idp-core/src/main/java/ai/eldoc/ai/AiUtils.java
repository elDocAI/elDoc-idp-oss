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
package ai.eldoc.ai;

import ai.eldoc.util.JsonbUtils;
import java.lang.reflect.Type;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Utility methods for AI-related workloads
 */
public class AiUtils {

    private static final String JSON_CODEBLOCK_PATTERN_STR = "(?s)```json(?<response>.*)```";
    private static final Pattern JSON_CODEBLOCK_PATTERN = Pattern.compile(JSON_CODEBLOCK_PATTERN_STR);

    private static final String MARKDOWN_CODEBLOCK_PATTERN_STR = "(?s)```markdown(?<response>.*)```";
    private static final Pattern MARKDOWN_CODEBLOCK_PATTERN = Pattern.compile(MARKDOWN_CODEBLOCK_PATTERN_STR);


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private AiUtils() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Removes optional Markdown Codeblocks wrapper around the actual JSON response
     *
     * @param response LLM response, that should be cleaned up
     * @return Cleaned LLM response
     */
    public static String removeJsonCodeblockFormatting(String response) {
        if (isBlank(response)) {
            return response;
        }
        Matcher matcher = JSON_CODEBLOCK_PATTERN.matcher(response);
        if (!matcher.matches()) {
            // No JSON Codeblocks found - return response as is
            return response.trim();
        }
        return matcher.group("response").trim();
    }


    /**
     * Removes optional Markdown Codeblocks wrapper around the actual JSON response
     *
     * @param response LLM response, that should be cleaned up
     * @return Cleaned LLM response
     */
    public static String removeMarkdownCodeblockFormatting(String response) {
        if (isBlank(response)) {
            return response;
        }
        Matcher matcher = MARKDOWN_CODEBLOCK_PATTERN.matcher(response);
        if (!matcher.matches()) {
            // No JSON Codeblocks found - return response as is
            return response.trim();
        }
        return matcher.group("response").trim();
    }


    public static <T> T parseJson(String value, Class<T> type) {
        String cleanedValue = removeJsonCodeblockFormatting(value);
        return JsonbUtils.getInstance().fromJson(cleanedValue, type);
    }


    public static <T> T parseJson(String value, Type type) {
        String cleanedValue = removeJsonCodeblockFormatting(value);
        return JsonbUtils.getInstance().fromJson(cleanedValue, type);
    }

}
