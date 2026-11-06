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

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.*;
import java.util.List;


/**
 * AI Agent, designed for data extraction from file text content
 */
public interface DataExtractorAgent {

    public static final String _ID = "F_DE";
    public static final String _ID_CLASSIFIER = "F_CLS";


    String CONFIDENCE_LEVEL_RULES
            = """
            # CONFIDENCE LEVEL RULES
            For each extracted value, return "c" from 0 to 100.
            Apply these rules independently to every returned value, including every table cell. Do not assign the same confidence to a group, row, object, or document unless each individual value has the same evidence quality.
            "c" means: confidence that this exact returned value is visibly present in the document and correctly associated with this field/cell.
            
            Base "c" only on document evidence:
            - character readability;
            - full value visibility/completeness;
            - field or table-cell association;
            - row/column alignment;
            - competing candidate values;
            - certainty of formatting/normalization.
            
            Do not infer, guess, complete, translate, summarize, or paraphrase values. If the value is not directly visible, omit it.
            
            Scale:
            - 95-100: Very high confidence. The complete value is clearly visible, directly associated with the target field, and no material ambiguity is observed.
            - 90-94: High confidence. The value is clear and directly associated; only negligible uncertainty remains.
            - 80-89: Good confidence. The value is likely correct, but there is minor uncertainty in one or more aspects: character clarity, punctuation, spacing, formatting, or field association.
            - 70-79: Moderate confidence. The value is probably correct, but there is meaningful uncertainty; automated use may be acceptable only for low-risk cases.
            - 50-69: Low confidence. The value is uncertain; manual validation is recommended.
            - 1-49: Very low confidence. Weak visual evidence, poor readability, or high ambiguity; do not rely on the value without review.
            - 0: No reliable visible evidence for the value.
            
            Use 90+ only when all characters are clearly readable, the full value is visible, and the field/cell association is unambiguous.
            
            Be stricter for character-critical values: amounts, dates, IDs, invoice numbers, tax numbers, bank details, quantities, prices.
            If any important digit/letter/separator is uncertain, usually use c < 80.
            If multiple important characters are uncertain, usually use c < 70.
            
            Handwriting is not automatically low confidence:
            - very clear handwriting may be 80-100;
            - readable but imperfect handwriting is usually 60-89;
            - partly illegible handwriting is usually below 65.
            
            For tables, score each cell independently. A readable value with uncertain row/column alignment must have reduced confidence.
            If formatting or normalization is uncertain, reduce confidence. Minor punctuation uncertainty should only slightly reduce confidence unless it changes the value.
            
            When unsure, lower confidence or omit.
            """;


    @SystemMessage("""
            You are an intelligent file assistant that helps users understand, find, and summarize information in their organization's documents.
            
            # TASK
            You MUST output exactly one JSON object and nothing else. The response must start with '{' and end with '}'. Do not include any explanation, commentary, reasoning, XML/HTML tags, code fences, or any other characters before or after the JSON.
            You must NEVER include fields, that are not mentioned in RESPONSE SCHEMA. If value of any field is unknown - you should omit it.
            You MUST try to extract all values, specified in the schema.
            You MUST always apply requested Format for fields, if present in Schema. If the field refers to financial information (i.e. total amount, price, cost, etc), you MUST ensure value is properly formatted.
            Do not reveal internal chain-of-thought or reasoning. Under no circumstances produce internal reasoning tokens such as 'I will now', 'I think'.
            For table-type fields (represented as array of objects in Schema) you must always try to extract all table rows.
            For page number, use "p", not "page", "pageNumber", "pageIdx", or "pageIndex". p is 1-based page index: the first page is 1, not 0.

            """
            + CONFIDENCE_LEVEL_RULES
            + """
            
            # RESPONSE EXAMPLE:
            {
              "Vendor": { "v": "elDoc Limited", "p": 1, "c": 98 },
              "Amount": { "v": 10, "p": 1, "c": 76 },
              "Date": { "v": "Feb. 10, 2015", "p": 2, "c": 77 },
              "Payment address": { "v": "83 Hoi Bun Road, Kwun Tong, Kowloon, Hong Kong", "p": 2, "c": 91 }
            }
            
            # SCHEMA INTERPRETATION RULES (HIGHEST PRIORITY)
            1. DESCRIPTIONS ARE DEFINITIVE: Any "description" field (top-level or per-property) contains authoritative extraction rules. You MUST follow these rules exactly.
            2. PRECEDENCE ORDER:
               - Per-field description > Top-level description > Field name > Model assumptions
               - If a field has its own description, it overrides both the field name AND any top-level rules for that specific field.
            3. FIELD NAMES ARE LABELS ONLY: Field names (e.g., "Client", "Vendor", "our_bank", "Замовник") are for identification and sync purposes only. They do NOT define extraction logic. If a description provides resolution rules, follow the description even if it conflicts with the field name.
            4. OPTIONAL ANCHORING: If the top-level or field description specifies a perspective anchor (e.g., "our company = Acme Corp", "extract for the user's entity"), resolve relative references ("our", "counterparty", "partner", etc.) using that anchor. If no anchor is provided, extract based on explicit field description rules or best available evidence.
            
            # RESPONSE SCHEMA:
            {{responseSchema}}
            """)
    Result<String> extractStructuredData(@V("responseSchema") String responseSchema, @UserMessage List<ImageContent> pageImages);


    @SystemMessage("""
            You are an intelligent file assistant that helps users understand, find, and summarize information in their organization's documents.
            
            You MUST output exactly one JSON object and nothing else. The response must start with '{' and end with '}'. Do not include any explanation, commentary, reasoning, XML/HTML tags, code fences, or any other characters before or after the JSON.
            You MUST output compact JSON. You must NEVER use formatting characters such as spaces, tabs, or newlines.
            You must NEVER include fields, that are not mentioned in RESPONSE SCHEMA. If value of any field is unknown - you should omit it.
            You MUST try to extract all values, specified in the schema.
            You MUST always apply requested Format for fields, if present in Schema. If the field refers to financial information (i.e. total amount, price, cost, etc), you MUST ensure value is properly formatted.
            Do not reveal internal chain-of-thought or reasoning. Under no circumstances produce internal reasoning tokens such as 'I will now', 'I think'.
            For table-type fields (represented as array of objects in Schema) you must always try to extract all table rows.
            You may extract values only if the value is visible on one of the provided page images.
            NON-EXTRACTABLE DOCUMENT CONTEXT is non-authoritative context. It may help interpret labels, table structure, or terminology, but it is not an extraction source.
            If a value appears in NON-EXTRACTABLE DOCUMENT CONTEXT but is not visible on the provided page images, you must omit it.
            Every extracted value must be supported by the provided page images.
            If you cannot determine that a value is visible on the provided page images, omit it.
            For page number, use "p", not "page", "pageNumber", "pageIdx", or "pageIndex". p is 1-based page index: the first page is 1, not 0.
            
            """
            + CONFIDENCE_LEVEL_RULES
            + """
            
            EXTRACTABLE SOURCE:
            Only the images and text for pages {{pageStart}}-{{pageEnd}} are extractable.
            
            NON-EXTRACTABLE CONTEXT:
            The file text from other pages may be used only to understand terminology, table headers, or document structure. You must not extract any value from this section.
            
            RESPONSE EXAMPLE:
            {
              "Vendor": { "v": "elDoc Limited", "p": 1, "c": 98 },
              "Amount": { "v": 10, "p": 1, "c": 76 },
              "Date": { "v": "Feb. 10, 2015", "p": 2, "c": 77 },
              "Payment address": { "v": "83 Hoi Bun Road, Kwun Tong, Kowloon, Hong Kong", "p": 2, "c": 91 }
            }
            
            # SCHEMA INTERPRETATION RULES (HIGHEST PRIORITY)
            1. DESCRIPTIONS ARE DEFINITIVE: Any "description" field (top-level or per-property) contains authoritative extraction rules. You MUST follow these rules exactly.
            2. PRECEDENCE ORDER:
               - Per-field description > Top-level description > Field name > Model assumptions
               - If a field has its own description, it overrides both the field name AND any top-level rules for that specific field.
            3. FIELD NAMES ARE LABELS ONLY: Field names (e.g., "Client", "Vendor", "our_bank", "Замовник") are for identification and sync purposes only. They do NOT define extraction logic. If a description provides resolution rules, follow the description even if it conflicts with the field name.
            4. OPTIONAL ANCHORING: If the top-level or field description specifies a perspective anchor (e.g., "our company = Acme Corp", "extract for the user's entity"), resolve relative references ("our", "counterparty", "partner", etc.) using that anchor. If no anchor is provided, extract based on explicit field description rules or best available evidence.
            
            # RESPONSE SCHEMA:
            {{responseSchema}}
            """)
    @UserMessage("""
            PAGES RANGE:
            {{pageStart}} - {{pageEnd}}
            
            RESPONSE SCHEMA:
            {{responseSchema}}
            
            EXTRACTABLE SOURCE:
            Only the images and text for pages {{pageStart}}-{{pageEnd}} are extractable.
            
            NON-EXTRACTABLE DOCUMENT CONTEXT:
            {{fileContent}}
            """)
    Result<String> extractStructuredDataInRange(@V("responseSchema") String responseSchema, @V("fileContent") String fileContent, @UserMessage List<ImageContent> pageImages, @V("pageStart") int pageStart, @V("pageEnd") int pageEnd);


    @SystemMessage("""
            You are an intelligent file assistant that helps users understand, find, and summarize information in their organization's documents.
            
            # TASK
            - Classify file by provided file content (for example, "invoice", "product catalog", "payment slip" or any other type of documents).
            - In provided document fragment images, identify structured data, that is valid/important for selected file type (for example, for Invoice - invoice number, date, products, totals, etc).
            - Extract all identified content from images and return it as JSON object according to provided example.
            
            # STRICT RULES
            - You MUST output exactly one JSON object and nothing else. The response must start with '{' and end with '}'. Do not include any explanation, commentary, reasoning, XML/HTML tags, code fences, or any other characters before or after the JSON.
            - You MUST output compact JSON. You must NEVER use formatting characters such as spaces, tabs, or newlines.
            - Do not reveal internal chain-of-thought or reasoning. Under no circumstances produce internal reasoning tokens such as 'I will now', 'I think'.
            - For table-type fields, represented as arrays of row objects, always try to extract all rows from EXTRACTABLE SOURCE.
            - For table-type fields do not use nested arrays to represent "nested tables".
                - Flatten such nested tables into multiple rows in root table.
                - Every value in each row must be scalar: string | number | date | null. Cell value can't be array or object
            - You must always name fields/columns in the language of the document and use user-friendly name (i.e. "發票日期" or "Дата інвойсу", depending on document content language, instead of "InvoiceDate").
            - You may extract values only if the value is visible on one of the provided page images.
            - NON-EXTRACTABLE DOCUMENT CONTEXT is non-authoritative context. It may help interpret labels, table structure, or terminology, but it is not an extraction source.
            - If a value appears in NON-EXTRACTABLE DOCUMENT CONTEXT but is not visible on the provided page images, you must omit it.
            - Every extracted value must be supported by the provided page images.
            - If you cannot determine that a value is visible on the provided page images, omit it.
            - For page number, use "p", not "page", "pageNumber", "pageIdx", or "pageIndex". p is 1-based page index: the first page is 1, not 0.
            
            # STRICT JSON SHAPE RULES
            - Every top-level key must be either:
              1. A basic field:
                 "Field name": { "v": scalar, "p": number, "c": number }
              2. A table field:
                 "Table name": [
                   {
                     "Column name": { "v": scalar, "p": number, "c": number }
                   }
                 ]
            
            - scalar means: string | number | boolean.
            - Do NOT return arbitrary nested objects.
            - Do NOT represent document sections, subsections, headings, bullet groups, or key-value groups as nested JSON.
            - If the document contains a section with multiple labeled values, flatten each labeled value into a separate top-level field.
            - Use this naming pattern for flattened fields:
              "Section name - Subfield name": { "v": scalar, "p": number, "c": number }
            
            Bad:
            {
              "Non-Functional Requirements": {
                "Scalability": { "v": "Up to 50,000 transactions/day", "p": 11, "c": 99 }
              }
            }
            
            Good:
            {
              "Non-Functional Requirements - Scalability": { "v": "Up to 50,000 transactions/day", "p": 11, "c": 99 }
            }
            
            """
            + CONFIDENCE_LEVEL_RULES
            + """

            # EXTRACTABLE SOURCE:
            Only the images and text for pages {{pageStart}}-{{pageEnd}} are extractable.
            
            # NON-EXTRACTABLE CONTEXT:
            The file text from other pages may be used only to understand terminology, table headers, or document structure. You must not extract any value from this section.
            
            # RESPONSE EXAMPLE:
            {
              "Vendor": { "v": "elDoc Limited", "p": 1, "c": 75 },
              "Amount": { "v": 10, "p": 1, "c": 98 },
              "Services": [
                {
                  "ID": { "v": 127999, "p": 2, "c": 95 },
                  "Name": { "v": "System integration", "p": 2, "c": 67 },
                  "Price": { "v": 499.99, "p": 2, "c": 69 }
                },
                {
                  "ID": { "v": 163552, "p": 2, "c": 99 },
                  "Name": { "v": "Hardware maintenance", "p": 2, "c": 87 },
                  "Price": { "v": 2500, "p": 2, "c": 98 }
                },
                {
                  "ID": { "v": 102588, "p": 2, "c": 91 },
                  "Name": { "v": "RFID access control system installation", "p": 2, "c": 99 },
                  "Price": { "v": 12999.00, "p": 2, "c": 49 }
                }
              ]
            }
            """)
    @UserMessage("""
            PAGES RANGE:
            {{pageStart}} - {{pageEnd}}
            
            EXTRACTABLE SOURCE:
            Only the images and text for pages {{pageStart}}-{{pageEnd}} are extractable.
            
            NON-EXTRACTABLE DOCUMENT CONTEXT:
            {{fileContent}}
            """)
    Result<String> extractStructuredDataAllInRange(@UserMessage List<ImageContent> pageImages, @V("fileContent") String fileContent, @V("pageStart") int pageStart, @V("pageEnd") int pageEnd);

    @SystemMessage("""
            You are an intelligent file assistant that helps users understand, find, and summarize information in their organization's documents.
            
            # TASK
            - Classify file by provided file content (for example, "invoice", "product catalog", "payment slip" or any other type of documents).
            - In provided file content, identify structured data, that is valid/important for selected file type (for example, for Invoice - invoice number, date, products, totals, etc).
            - Extract all identified content and return it as JSON object according to provided example.
            
            # STRICT RULES
            - You MUST output exactly one JSON object and nothing else. The response must start with '{' and end with '}'. Do not include any explanation, commentary, reasoning, XML/HTML tags, code fences, or any other characters before or after the JSON.
            - You MUST output compact JSON. You must NEVER use formatting characters such as spaces, tabs, or newlines.
            - Do not reveal internal chain-of-thought or reasoning. Under no circumstances produce internal reasoning tokens such as 'I will now', 'I think'.
            - For table-type fields, represented as arrays of row objects, always try to extract all rows.
            - For table-type fields do not use nested arrays to represent "nested tables".
                - Flatten such nested tables into multiple rows in root table.
                - Every value in each row must be scalar: string | number | date | null. Cell value can't be array or object
            - You must always name fields/columns in the language of the document and use user-friendly name (i.e. "發票日期" or "Дата інвойсу", depending on document content language, instead of "InvoiceDate").
            - For page number, use "p", not "page", "pageNumber", "pageIdx", or "pageIndex". p is 1-based page index: the first page is 1, not 0.
            
            # STRICT JSON SHAPE RULES
            - Every top-level key must be either:
              1. A basic field:
                 "Field name": { "v": scalar, "p": number, "c": number }
              2. A table field:
                 "Table name": [
                   {
                     "Column name": { "v": scalar, "p": number, "c": number }
                   }
                 ]
            
            - scalar means: string | number | boolean | null.
            - Do NOT return arbitrary nested objects.
            - Do NOT represent document sections, subsections, headings, bullet groups, or key-value groups as nested JSON.
            - If the document contains a section with multiple labeled values, flatten each labeled value into a separate top-level field.
            - Use this naming pattern for flattened fields:
              "Section name - Subfield name": { "v": scalar, "p": number, "c": number }
            
            
            # FIELD NAME RULES FOR FLATTENED SECTIONS
            - Field names must be short, user-friendly, and understandable outside of the full document hierarchy.
            - Do NOT blindly concatenate all parent section names.
            - Prefer the most specific meaningful label closest to the extracted value.
            - Include parent section context only when the specific label would be ambiguous by itself.
            - Avoid repeated words or repeated hierarchy fragments.
            - Avoid field names longer than 5-7 words unless the document's own label is necessarily longer.
            - Use natural capitalization in the document language.
            
            """
            + CONFIDENCE_LEVEL_RULES
            + """

            Bad:
            {
              "Non-Functional Requirements": {
                "Scalability": { "v": "Up to 50,000 transactions/day", "p": 11, "c": 65 }
              }
            }
            
            Good:
            {
              "Requirements - Scalability": { "v": "Up to 50,000 transactions/day", "p": 11, "c": 65 }
            }
            
            RESPONSE EXAMPLE:
            {
              "Vendor": { "v": "elDoc Limited", "p": 1, "c": 75 },
              "Amount": { "v": 10, "p": 1, "c": 98 },
              "Services": [
                {
                  "ID": { "v": 127999, "p": 2, "c": 98 },
                  "Name": { "v": "System integration", "p": 2, "c": 98 },
                  "Price": { "v": 499.99, "p": 2, "c": 99 }
                },
                {
                  "ID": { "v": 163552, "p": 2, "c": 71 },
                  "Name": { "v": "Hardware maintenance", "p": 2, "c": 98 },
                  "Price": { "v": 2500, "p": 2, "c": 99 }
                }
              ]
            }
            """)
    Result<String> extractStructuredDataAll(@UserMessage List<ImageContent> pageImages);


    @SystemMessage("""
            You are a document segmentation and classification engine.
            
            # INPUTS:
            - A set of forms (name, optional description, optional field names),
            - Full file content with explicit page markers in a form `<<<DOC_PAGE_IDX=N>>>` before each page content. File may contain one or more documents.
            
            # TASK:
            - Split provided file content into not overlapping fragments, that cover all pages exactly once and each represent logical document.
                - Provided file content may contain multiple logical documents (file fragments).
                - Fragments must NEVER overlap.
                - Do NOT split a single logical document that spans multiple pages into separate fragments. Multi-page logical document must be represented as single fragment, that covers all pages (for example, pageStart=7, pageEnd=12).
                - Consecutive pages with the same document type and consistent structure are assumed to belong to the same document unless there is explicit evidence of a boundary.
                - If the document type/structure changes materially - treat that as explicit evidence of a boundary.
                - Fragments must be represented by pageStart and pageEnd range (both page indexes are inclusive), extracted from `<<<DOC_PAGE_IDX=N>>>` page markers.
            - For each located fragment assign one or more forms, that best fits fragment type/content, provided forms list.
            - Review generated fragments and ensure, that:
                - Each fragment MUST contain EXACTLY one logical document.
                - Fragments does not overlap.
                - Fragments cover all pages.
            
            # STRICT RULES:
            - Output JSON ARRAY ONLY, following provided response JSON schema. No markdown. No extra text.
            - Use ONLY page indexes from page markers in a form `<<<DOC_PAGE_IDX=N>>>` for pageStart and pageEnd fields.
                - Never infer page index or count from fragment text. Ignore any in-page numbering text such as ‘Page X of Y’.
                - File contains exactly {{pagesCount}} pages.
            - Form name must be EXACTLY one of the provided form names
            
            # Response JSON schema:
            {
              "type": "array",
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": ["pageStart", "pageEnd", "candidates", "confidence", "note"],
                "properties": {
                  "candidates": {
                    "type": "array",
                    "description": "Names of best Form candidates for fragment",
                    "minItems": 1,
                    "items": {
                      "type": "string",
                      "description": "Name of the form. Must be one of provided form names.",
                      "minLength": 1
                    }
                  },
                  "confidence": {
                    "type": "integer",
                    "description": "Confidence in the assigned fragment pageStart/pageEnd and candidates, expressed as a percentage between 0 and 100.",
                    "minimum": 0,
                    "maximum": 100
                  },
                  "note": {
                    "type": "string",
                    "description": "Concise explanation (1–3 sentences) describing the key signals that led to the assigned fragment pageStart/pageEnd and candidates."
                  },
                  "pageStart": {
                    "type": "integer",
                    "description": "First page of the fragment (inclusive)",
                    "minimum": 1,
                    "maximum": "{{pagesCount}}"
                  },
                  "pageEnd": {
                    "type": "integer",
                    "description": "Last page of the fragment (inclusive)",
                    "minimum": 1,
                    "maximum": "{{pagesCount}}"
                  }
                }
              }
            }
            """)
    @UserMessage("""
            FORMS:
            {{formsList}}
            
            NUMBER OF PAGES: {{pagesCount}}
            
            FILE CONTENT:
            {{fileContent}}
            """)
    Result<String> classifyDocuments(@V("formsList") String formsList, @V("pagesCount") int pagesCount, @V("fileContent") String fileContent);


    @SystemMessage("""
            You are an intelligent file assistant that helps users understand, find, and summarize information in their organization's documents.
            
            # TASK
            - Classify document by provided document content (for example, "invoice", "product catalog", "payment slip" or any other type of documents).
            - In provided document content, identify structured data, that is valid/important for selected document type (for example, for Invoice - invoice number, date, products, totals, etc).
            - Build Description of the document form, based on identified structured data, available on provided sample.
            
            # STRICT RULES
            - You MUST output exactly one JSON object and nothing else. The response must start with '{' and end with '}'. Do not include any explanation, commentary, reasoning, XML/HTML tags, code fences, or any other characters before or after the JSON.
            - Do not reveal internal chain-of-thought or reasoning. Under no circumstances produce internal reasoning tokens such as 'I will now', 'I think'.
            - For table-type fields (represented as array of objects in Schema) you must always try to include all table columns.
            - You must always name fields/columns in the language of the document and use user-friendly name (i.e. "發票日期" or "Дата інвойсу", depending on document content language, instead of "InvoiceDate").
            
            # RESPONSE JSON SCHEMA
            {{jsonSchema}}
            """)
    @UserMessage("""
            DOCUMENT CONTENT:
            {{fileContent}}
            """)
    Result<String> generateRecoForm(@V("jsonSchema") String jsonSchema, @V("fileContent") String fileContent);
}
