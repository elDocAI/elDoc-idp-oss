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

import java.io.Serial;
import java.io.Serializable;
import java.util.*;


/**
 *
 */
public class RecoFormAiDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 4372223927497604494L;

    private String _name;
    private List<String> _tags;
    private List<RecoFieldAiDTO> _fields;
    private List<RecoFieldTableAiDTO> _tables;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoFormAiDTO() {
        _tags = new ArrayList<>();
        _fields = new ArrayList<>();
        _tables = new ArrayList<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static String toJsonSchema(boolean withFieldArea) {
        return """
                {
                  "type": "object",
                  "required": ["name", "tags", "fields", "tables"],
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "Name of the form (In language of the document content)"
                    },
                    "tags": {
                      "type": "array",
                      "description": "Tags of the form (unique, in English language)",
                      "items": {
                        "type": "string",
                        "description": "Form tag"
                        "minItems": 1,
                        "maxItems": 4
                      }
                    },
                    "fields": {
                      "type": "array",
                      "description": "List of simple text fields",
                      "items": %s
                    },
                    "tables": {
                      "type": "array",
                      "description": "List of table fields with columns",
                      "items": %s
                    }
                  },
                  "additionalProperties": false
                }
                """.formatted(RecoFieldAiDTO.toJsonSchema(withFieldArea), RecoFieldTableAiDTO.toJsonSchema(withFieldArea));
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


    public List<RecoFieldAiDTO> getFields() {
        return _fields;
    }


    public void setFields(List<RecoFieldAiDTO> fields) {
        _fields = fields;
    }


    public List<RecoFieldTableAiDTO> getTables() {
        return _tables;
    }


    public void setTables(List<RecoFieldTableAiDTO> tables) {
        _tables = tables;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RecoFormAiDTO that = (RecoFormAiDTO) o;
        return Objects.equals(_name, that._name)
                && Objects.equals(_tags, that._tags)
                && Objects.equals(_fields, that._fields)
                && Objects.equals(_tables, that._tables);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_name, _tags, _fields, _tables);
    }
}
