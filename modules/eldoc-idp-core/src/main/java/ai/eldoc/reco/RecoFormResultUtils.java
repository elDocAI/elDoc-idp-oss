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
package ai.eldoc.reco;

import ai.eldoc.model.reco.*;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Utility class with helper methods for {@link RecoFormResult} and its parts ({@link RecoPageResult},
 * {@link RecoFieldResult})
 */
public class RecoFormResultUtils {


    private RecoFormResultUtils() {
        /* This utility class should not be instantiated */
    }


    //
    // RecoFieldResult-related methods ------------------------------------------------------------
    //


    /**
     * Checks if two RecoFieldResults are equal by comparing two RecoFieldResult values, taking into
     * account fields type and structure, where applicable
     *
     * @param first
     * @param second
     * @param compareFieldsDef if true, additional checks, that RecoFieldResults has same definition
     * (name, properties, etc.) is performed
     * @return
     */
    public static boolean equals(RecoFieldResult first, RecoFieldResult second, boolean compareFieldsDef) {
        if (first == null && second == null) {
            // Both fields are null - treat them as equal
            return true;
        }

        if ((first == null || second == null)) {
            // One of the fields is null, and other is not - treat them, as different
            return false;
        }

        if (first.getType() != second.getType()) {
            // Currently, fields of different type can't have same values by definition
            return false;
        }

        if (compareFieldsDef && !isFieldsDefMatches(first, second, true)) {
            // Fields have different setup, like name, properties or RecoForm page
            return false;
        }

        if (first.isTypeText()) {
            // Text fields should be compared directly be their values
            if (!Objects.equals(first.getValue(), second.getValue())) {
                return false;
            }
        } else if (first.isTypeOmr()) {
            // OMR field values are, in fact, an array of optical marks, each with its own name, settings
            // and values. They should be compared by matching each Mark's settings and values
            if (!isOmrFieldValueEquals(first, second)) {
                return false;
            }
        } else if (first.isTypeTable()) {
            // Comparing table fields is not supported by this method
            // TODO: Check, why this block is empty and isTableFieldValueEquals method is not used (requires testing)
        }

        return true;
    }


    /**
     * Checks whether a field was matched (located) on the document page or not
     *
     * @param recoFieldResult
     * @return
     */
    public static boolean isMatched(RecoFieldResult recoFieldResult) {
        if (recoFieldResult == null) {
            return false;
        }

        return recoFieldResult.getMatchConfidence() != RecoFieldResult.FIELD_NOT_FOUND_CONFIDENCE;
    }


    /**
     * Inits default instance using the data from the provided RecoForm
     *
     * @param recoForm
     * @return
     */
    public static RecoFormResult initWithRecoForm(RecoForm recoForm) {
        RecoFormResult recoFormResult = new RecoFormResult();
        recoFormResult.setRecoForm(recoForm);
        recoFormResult.setRecoFormConfidence(0);

        recoForm.getPages().forEach(recoPage -> {
            RecoPageResult recoPageResult = new RecoPageResult(recoForm.getPages().indexOf(recoPage));
            recoPage.getFields().stream()
                    .map(rf -> RecoFieldResult.createNotFound(recoForm, rf))
                    .forEach(recoPageResult.getFields()::add);
            recoFormResult.getPages().add(recoPageResult);
        });

        return recoFormResult;
    }


    /**
     *
     * @param recoFormResult
     * @return confidence level for provided RecoFormResult
     */
    public static double calculateMatchConfidence(RecoFormResult recoFormResult) {
        return recoFormResult.getFields().parallelStream()
                .filter(fieldResult -> recoFormResult.getRecoFieldByResult(fieldResult).isRequired() || RecoFormResultUtils.isMatched(fieldResult))
                .mapToDouble(RecoFieldResult::getMatchConfidence)
                .average().orElse(0);
    }


    /**
     * Calculates maximum confidence level of the form that can be reached if all fields that should be still matched are matched with 100% confidence level
     *
     * @param recoFormResult {@link RecoFormResult} for which maximum possible match confidence level should be calculated
     * @return Maximum confidence level that can be reached by the form
     */
    public static double calculateMatchConfidenceMax(RecoFormResult recoFormResult) {

        List<RecoFieldResult> recoFormResultFieldsCopy = new ArrayList<>();
        List<RecoPageResult> recoFormResultPagesCopy = new ArrayList<>(recoFormResult.getPages());
        for (RecoPageResult recoPageResult : recoFormResultPagesCopy) {
            recoFormResultFieldsCopy.addAll(recoPageResult.getFields());
        }
        int totalMatchedFieldsCount = recoFormResultFieldsCopy.size();

        List<RecoFieldResult> meaningfulMatchedFields = recoFormResultFieldsCopy.stream()
                .filter(fieldResult -> recoFormResult.getRecoFieldByResult(fieldResult).isRequired() || RecoFormResultUtils.isMatched(fieldResult))
                .toList();
        int meaningfulMatchedFieldsCount = meaningfulMatchedFields.size();

        double matchedFieldsConfidence = meaningfulMatchedFields.stream()
                .mapToDouble(RecoFieldResult::getMatchConfidence)
                .average().orElse(0);
        int fieldsLeftToMatch = recoFormResult.getRecoForm().getFields().size() - totalMatchedFieldsCount;
        int meaningfulFieldsCount = fieldsLeftToMatch + meaningfulMatchedFieldsCount;
        return (matchedFieldsConfidence * meaningfulMatchedFieldsCount + 100 * fieldsLeftToMatch) / meaningfulFieldsCount;
    }


    //
    // RecoFieldResult-related internal methods ---------------------------------------------------
    //


    /**
     * Compares two RecoFieldResults definitions (name, properties, etc.)
     *
     * @param first
     * @param second
     * @param strict if true, additionally requires a referenced RecoForm page and field index to match
     * @return
     */
    static boolean isFieldsDefMatches(RecoFieldResult first, RecoFieldResult second, boolean strict) {
        if (!first.getName().equals(second.getName())) {
            return false;
        }

        if (first.getType() != second.getType()) {
            return false;
        }

        if (strict && first.getRfPageIdx() != second.getRfPageIdx()) {
            return false;
        }

        if (strict && first.getRfFieldIdx() != second.getRfFieldIdx()) {
            return false;
        }

        return first.getProperties().equals(second.getProperties());
    }


    /**
     * Compares two OMR-type RecoFieldResults values.<br>
     * As an OMR-type field is, in fact, a list of optical marks, such fields are equals if and
     * only if each mark value is equals.<br>
     * <b>IMPORTANT:</b> this method does not require matching order of optical marks for both fields,
     * instead finding matching optical marks by their setup to provide more flexibility
     *
     * @param first
     * @param second
     * @return
     */
    static boolean isOmrFieldValueEquals(RecoFieldResult first, RecoFieldResult second) {
        if (first.getOmrData().size() != second.getOmrData().size()) {
            // Fields have different number of marks and can't be equal
            return false;
        }

        // Compare each optical mark. Relying on compareFieldResults also ensures each mark name and properties matches
        for (int i = 0; i < first.getOmrData().size(); i++) {
            RecoFieldResult firstOpMark = first.getOmrData().get(i);
            RecoFieldResult secondOpMark = findMatchingOpMark(firstOpMark, second);
            if (!equals(firstOpMark, secondOpMark, true)) {
                return false;
            }
        }

        return true;
    }


    /**
     * Finds matching optical mark in the provided OMR field, selecting by RecoField definition
     *
     * @param opMark
     * @param secondOmrField
     * @return
     */
    static RecoFieldResult findMatchingOpMark(RecoFieldResult opMark, RecoFieldResult secondOmrField) {
        return secondOmrField.getOmrData().stream()
                .filter(secondOpMark -> RecoFormResultUtils.isFieldsDefMatches(opMark, secondOpMark, false))
                .findFirst().orElse(null);
    }


    /**
     * Reorders RecoFieldResults in the provided table row to match the structure of the table, as defined by table's RecoField
     *
     * @param recoForm
     * @param tableField
     * @param colsResult
     * @return
     */
    public static List<RecoFieldResult> reorderResultCols(RecoForm recoForm, RecoField tableField, List<RecoFieldResult> colsResult) {
        return tableField.getColumns().stream()
                .map(col -> colsResult.stream()
                        .filter(rfr -> Objects.equals(rfr.getName(), col.getAnchorText()))
                        .findFirst().orElse(RecoFieldResult.createNotFound(recoForm, tableField)))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
