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
package ai.eldoc.ai.assistants;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.*;


/**
 * Assistant, tailored for performing OCR tasks on the image. Intended to be used with Vision-enabled models
 */
public interface OcrAssistant {

    public static final String _ID = "OCR";

    @UserMessage("Provide transcriptions and bounding boxes for the words in the image. Use JSON format. Use array for bounding boxes. Response must ALWAYS include all text,that is present on the image.")
    Result<String> doOcr(@UserMessage ImageContent image);

    @UserMessage("Provide transcriptions of the image in Markdown format. Response must ALWAYS include all text,that is present on the image.")
    Response<AiMessage> doOcrMarkdown(@UserMessage ImageContent image);


    @SystemMessage("""
            STRICT RULES:
            - Analyze text on the provided image and decide based on analysis results;
            - You must ALWAYS respond with valid JSON object, that contains page orientation and confidence level for it.
            - Available orientations:
                 - 'NO_ROTATION' - page is already in proper reading orientation;
                 - 'ROTATE_RIGHT_90' - page must be rotated right by 90 degree to get proper reading direction;
                 - 'ROTATE_LEFT_90' - page must be rotated left by 90 degree to get proper reading direction;
                 - 'ROTATE_180' - page must be rotated 180 degree to get proper reading direction;

            Example 1:
            Image: page with upright printed English text
            Orientation: {"orientation": "NO_ROTATION", "confidence": 0.83}

            Example 2:
            Image: scanned page where printed text baseline points upward 90° clockwise
            Orientation: {"orientation": "ROTATED_RIGHT_90", "confidence": 1.0}

            Example 3:
            Image: scanned page where printed text baseline is horizontal, but characters turned upside down and text goes from right to left
            Orientation: {"orientation": "ROTATED_180", "confidence": 0.2}

            Example 4:
            Image: page with numbers and tables rotated left 90°
            Orientation: {"orientation": "ROTATED_LEFT_90", "confidence": 0.95}
            """)
    @UserMessage("""
            Analyze provided page and return its rotation relatively to proper orientation.
            """)
    Response<String> getOrientation(@UserMessage ImageContent image);

}
