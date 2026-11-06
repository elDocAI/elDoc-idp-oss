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
package ai.eldoc.ai.assistants.dto.reco;

import ai.eldoc.model.reco.Rectangle;
import jakarta.json.bind.annotation.JsonbTransient;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;


/**
 *
 */
public class RecoFieldAiDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = -602117189629886525L;

    private String _name;
    private List<String> _tags;
    private String _description;
    private List<Integer> _fieldArea;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoFieldAiDTO() {
        _tags = new ArrayList<>();
        _fieldArea = new ArrayList<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @JsonbTransient
    public Rectangle getFieldValueRect() {
        if (_fieldArea.size() != 4) {
            return new Rectangle();
        }

        return new Rectangle(_fieldArea.get(0), _fieldArea.get(1), _fieldArea.get(2), _fieldArea.get(3));
    }


    public static String toJsonSchema(boolean withFieldArea) {
        if (withFieldArea) {
            return toJsonSchemaWithFieldArea();
        }
        return toJsonSchema();
    }


    private static String toJsonSchema() {
        return """
                {
                  "type": "object",
                  "required": ["name", "description", "tags"],
                  "properties": {
                    "name": { "type": "string", "description": "User-friendly field name (1-2 words, in language of the document content)" },
                    "description": { "type": "string", "description": "Description of the field content (short sentence, in language of the document content)" },
                    "tags": {
                      "type": "array",
                      "description": "Tags of the field (unique)",
                      "items": {
                        "type": "string",
                        "description": "Field tag (single word, in English language)"
                        "minItems": 1,
                        "maxItems": 4
                      }
                    }
                  },
                  "additionalProperties": false
                }
                """;
    }


    private static String toJsonSchemaWithFieldArea() {
        return """
                {
                  "type": "object",
                  "required": ["name", "description", "tags", "fieldRect"],
                  "properties": {
                    "name": { "type": "string" },
                    "description": { "type": "string" },
                    "tags": {
                      "type": "array",
                      "description": "Tags of the form (unique)",
                      "items": {
                        "type": "string",
                        "minItems": 1,
                        "maxItems": 4
                      }
                    },
                    "fieldRect": {
                      "type": "array",
                      "items": { "type": "integer" },
                      "minItems": 4,
                      "maxItems": 4
                    }
                  },
                  "additionalProperties": false
                }
                """;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getName() {
        return _name;
    }


    public void setName(String name) {
        _name = name;
    }


    public List<String> getTags() {
        return _tags;
    }


    public void setTags(List<String> tags) {
        _tags = tags;
    }


    public String getDescription() {
        return _description;
    }


    public void setDescription(String description) {
        _description = description;
    }


    public List<Integer> getFieldArea() {
        return _fieldArea;
    }


    public void setFieldArea(List<Integer> fieldArea) {
        _fieldArea = fieldArea;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RecoFieldAiDTO that = (RecoFieldAiDTO) o;
        return Objects.equals(_name, that._name)
                && Objects.equals(_tags, that._tags)
                && Objects.equals(_description, that._description)
                && Objects.equals(_fieldArea, that._fieldArea);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_name, _tags, _description, _fieldArea);
    }
}
