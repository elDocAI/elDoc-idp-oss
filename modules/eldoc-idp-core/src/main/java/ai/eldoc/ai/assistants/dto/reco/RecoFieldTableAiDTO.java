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
import java.util.*;


/**
 *
 */
public class RecoFieldTableAiDTO extends RecoFieldAiDTO {

    @Serial
    private static final long serialVersionUID = -4889121444018511944L;

    private List<RecoFieldAiDTO> _columns;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoFieldTableAiDTO() {
        super();
        _columns = new ArrayList<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static String toJsonSchema(boolean withFieldArea) {
        if (withFieldArea) {
            return toJsonSchemaWithFieldArea();
        }
        return toJsonSchema();
    }


    public static String toJsonSchema() {
        return """
               {
                 "type": "object",
                 "required": ["name", "description", "tags"],
                 "properties": {
                   "name": { "type": "string", "description": "User-friendly table name (1-2 words, in language of the document content)" },
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
                   },
                   "columns": {
                     "type": "array",
                     "items": {
                       "type": "object",
                       "required": ["name", "description", "tags"],
                       "properties": {
                         "name": { "type": "string", "description": "User-friendly column name (1-2 words, in language of the document content)" },
                         "description": { "type": "string", "description": "Description of the column content (short sentence, in language of the document content)" },
                         "tags": {
                           "type": "array",
                           "description": "Tags of the column (unique)",
                           "items": {
                             "type": "string",
                             "description": "Column tag (single word, in English language)"
                             "minItems": 1,
                             "maxItems": 4
                           }
                         }
                         "additionalProperties": false
                       }
                     }
                   }
                 },
                 "additionalProperties": false
               }
               """;
    }


    public static String toJsonSchemaWithFieldArea() {
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
                   "columns": {
                     "type": "array",
                     "items": {
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
    public List<RecoFieldAiDTO> getColumns() {
        return _columns;
    }


    public void setColumns(List<RecoFieldAiDTO> columns) {
        _columns = columns;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RecoFieldTableAiDTO that = (RecoFieldTableAiDTO) o;
        return Objects.equals(_columns, that._columns);
    }


    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _columns);
    }
}
