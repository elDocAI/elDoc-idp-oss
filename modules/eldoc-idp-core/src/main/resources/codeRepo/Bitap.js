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
/**
 * BITAP fuzzy search implementation of Wu-Manber algorithm. Finds all approximate (within a given 
 * Levenshtein distance) matches of a needle within a haystack.
 * Except of general symbols, next symbol groups can be used:
 * <ul>
 * <li>\d - any digit (i.e. [0-9]);</li>
 * <li>\D - any symbol, except digits (i.e. [^0-9]);</li>
 * <li>\x - any latin letter (i.e. [a-zA-Z]);</li>
 * </ul>
 * 
 * @type type
 */
class Bitap {
    
    static javaBitapImpl;

    /**
     * Returns the index within this string of the first occurrence of the specified substring,
     * located with fuzzy search algorithm (allowing mistakes in located match, i.e., 'd0cument' 
     * can be located with confidence 87.5% confidence, if searching for 'document').<br>
     * If there is multiple results located, only result with lowest number of errors returned.<br>
     * If there is multiple results with same (lowest) number of errors, then first occurred result is returned.<br>
     * If no result found, then -1 is returned.
     * 
     * @param {String} haystack String, where occurences of Needle will be searched
     * @param {String} needle The substring to search for
     * @param {Number} maxMistakesCount Maximum number of mistakes allowed
     * @returns {Number} offset of the located substring (needle) or -1, if no matches was found
     */
    static indexOf(haystack, needle, maxMistakesCount) {
        if (typeof Bitap.javaBitapImpl == 'undefined') {
            Bitap.javaBitapImpl = (Java.type("ai.eldoc.reco.Bitap"));
        }
        var locatedResult = Bitap.javaBitapImpl.findPostProcess(haystack, needle, maxMistakesCount);
        if (locatedResult) {
            return locatedResult.getPosition();
        } else {
            return -1;
        }
    }

    /**
     * Performs check, if string (haystack) contains provided substring. Search is performed via fuzzy search algorithm, allowing
     * mistackes in searched substring (i.e., 'd0cument' can be located with confidence 87.5% confidence, if searching for 'document')
     * 
     * @param {String} haystack String, where occurences of Needle will be searched
     * @param {String} needle The substring to search for
     * @param {Number} maxMistakesCount Maximum number of mistakes allowed
     * @returns {Boolean} true, if haystack contains searched substring (needle), or false otherwise
     */
    static contains(haystack, needle, maxMistakesCount) {
        return Bitap.indexOf(haystack, needle, maxMistakesCount) !== -1;
    }

    /**
     * Performs fuzzy search and replacement of first match.<br>
     * If no matches found, original value returned.
     * 
     * @param {String} haystack string, where occurences of Needle will be searched and replaced, if found
     * @param {String} needle The substring to search for
     * @param {Number} maxMistakesCount Maximum number of mistakes allowed
     * @param {String} newValue Replacement string to be used. If no new value provided, needle is used as replacement string
     * @returns {String} Haystack with first located match of needle replaced, or original haystack value, if no matches found
     */
    static replace(haystack, needle, maxMistakesCount, newValue = needle) {
        if (typeof Bitap.javaBitapImpl == 'undefined') {
            Bitap.javaBitapImpl = (Java.type("ai.eldoc.reco.Bitap"));
        }
        return Bitap.javaBitapImpl.replacePostProcess(haystack, needle, maxMistakesCount, newValue);
    }
}

