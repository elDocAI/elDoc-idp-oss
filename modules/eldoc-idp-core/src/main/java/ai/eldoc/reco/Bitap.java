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

import ai.eldoc.exceptions.BitapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;


/**
 * Provides general implementation of Bitap fuzzy search algorithm
 */
public class Bitap {

    private static final Logger LOG = LoggerFactory.getLogger(Bitap.class);


    private static final int MAX_ALLOWED_NEEDLE_LENGTH = 15;
    private static final int MIN_ALLOWED_WORD_LENGTH = 5;

    private static final Map<Character, List<Character>> CHARACTER_CLASSES = new HashMap<>();

    private int _needleLen;
    private Map<Character, Long> _alphabetMasks;
    List<BitapResult> _locatedPositions = new ArrayList<>();


    static {
        fillPredefinedClasses();
    }


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public Bitap() {
        _alphabetMasks = new HashMap<>();
        fillPredefinedClasses();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Finds provided string (needle) in haystack with number of mistakes less then or equal to provided
     * threshold.<br>
     * Number of mistakes calculated as Levenstein distance.
     *
     * @param haystack
     * @param needle
     * @param maxMistakesCount
     * @return
     */
    public static BitapResult find(String haystack, String needle, int maxMistakesCount) {
        int maxPatternLength = needle.length() + maxMistakesCount;

        if (haystack.length() < maxPatternLength) {
            return new Bitap().findInner(haystack, needle, maxMistakesCount);
        }

        List<BitapResult> locatedResultList = new ArrayList<>();
        for (int i = 0; i < haystack.length() - maxPatternLength + 1; i++) {
            String testedString = haystack.substring(i, i + maxPatternLength);
            BitapResult locatedResult = new Bitap().findInner(testedString, needle, maxMistakesCount);
            if (locatedResult != null) {
                locatedResult.setPosition(locatedResult.getPosition() + i);
                locatedResultList.add(locatedResult);
            }
        }

        if (locatedResultList.isEmpty()) {
            return null;
        }

        BitapResult bestResult = locatedResultList.get(0);
        for (BitapResult result : locatedResultList) {
            if (result.getDistance() < bestResult.getDistance()) {
                bestResult = result;
            }
        }

        return bestResult;
    }


    /**
     * Finds provided string (needle) in haystack with number of mistakes less then or equal to provided
     * threshold.<br>
     * Number of mistakes calculated as Levenstein distance.<br>
     * <b>IMPORTANT:</b> Maximum allowed needle length is limited 15. Maximum number of mistakes depends on
     * needle length
     *
     * @param haystack
     * @param needle
     * @param maxMistakesCount
     * @return
     * @throws BitapException if provided needle is to long
     */
    public static BitapResult findPostProcess(String haystack, String needle, int maxMistakesCount) throws BitapException {
        try {
            if (!isAllowedNeedleLength(needle)) {
                throw new BitapException("Bitap: search string is to long, maximum allowed length=" + MAX_ALLOWED_NEEDLE_LENGTH);
            }
            int normalizedMistakesCount = normalizeThreshold(needle.length(), maxMistakesCount);

            List<BitapResult> locatedResultList = new ArrayList<>();

            List<BitapTextPosition> testStringsList = splitString(haystack);
            filterTextPositions(testStringsList, needle.length(), normalizedMistakesCount);
            testStringsList.parallelStream().forEach(textPosition -> {
                BitapResult bitapResult = find(textPosition.getText(), needle, normalizedMistakesCount);
                if (bitapResult != null) {
                    bitapResult.setPosition(textPosition.getPosition() + bitapResult.getPosition());
                    locatedResultList.add(bitapResult);
                }
            });

            if (locatedResultList.isEmpty()) {
                return null;
            }

            BitapResult bestResult = locatedResultList.get(0);
            for (BitapResult result : locatedResultList) {
                if (result.getDistance() < bestResult.getDistance()) {
                    bestResult = result;
                }
            }

            return bestResult;
        } catch (BitapException bex) {
            throw bex;
        } catch (Throwable te) {
            LOG.error("Params: {} | {} | {}", haystack, needle, maxMistakesCount, te);
            throw te;
        }
    }


    static void filterTextPositions(List<BitapTextPosition> textPositionsList, int needleLen, int maxMistakesCount) {
        textPositionsList.removeIf(textPosition -> textPosition.getText().length() < needleLen - maxMistakesCount || textPosition.getText().length() > needleLen + maxMistakesCount);
    }


    static List<BitapTextPosition> splitString(String haystack) {
        List<BitapTextPosition> textPositionList = new ArrayList<>();

        String[] words = haystack.split(" ");

        for (int i = 0; i < words.length; i++) {
            if (words[i].length() >= MIN_ALLOWED_WORD_LENGTH) {
                // Current word already has allowed length
                BitapTextPosition textPosition = new BitapTextPosition(words[i], getStartPosition(words, i));
                textPositionList.add(textPosition);
            } else {
                textPositionList.addAll(getExtendedWordCombinations(words, i));
            }
        }

        return textPositionList;
    }


    /**
     * Provided word at position [idx] is to short, tries to "merge" it with adjacent words (in backward
     * or forward direction) to proper length and returns all possible combinations
     *
     * @param words
     * @param idx
     * @return
     */
    static List<BitapTextPosition> getExtendedWordCombinations(String[] words, int idx) {
        List<BitapTextPosition> textPositionList = new ArrayList<>();

        // Try moving backward
        String combinedString = "";
        for (int i = idx; i >= 0; i--) {
            combinedString = words[i] + " " + combinedString;
            if (combinedString.replaceAll(" ", "").length() >= MIN_ALLOWED_WORD_LENGTH) {
                BitapTextPosition textPosition = new BitapTextPosition(combinedString.trim(), getStartPosition(words, i));
                textPositionList.add(textPosition);
                break;
            }
        }
        // Try moving forward
        combinedString = "";
        for (int i = idx; i < words.length; i++) {
            combinedString = combinedString + " " + words[i];
            if (combinedString.replaceAll(" ", "").length() >= MIN_ALLOWED_WORD_LENGTH) {
                BitapTextPosition textPosition = new BitapTextPosition(combinedString.trim(), getStartPosition(words, idx));
                textPositionList.add(textPosition);
                break;
            }
        }

        return textPositionList;
    }


    /**
     * Calculates staring index of the word in original string, taking into account, that
     * provided words array is is created as OriginalString.split(" ") - meaning, joining
     * all words from words array and adding spaces between them will result in original
     * string
     *
     * @param words
     * @param idx
     * @return
     */
    static int getStartPosition(String[] words, int idx) {
        if (idx >= words.length) {
            return -1;
        }

        int currentSourcePosition = 0;
        for (int i = 0; i < idx; i++) {
            // Add the length of current word
            currentSourcePosition += words[i].length();
            // Add space after current word
            currentSourcePosition++;
        }

        return currentSourcePosition;
    }


    /**
     * Finds provided string (needle) in haystack with number of mistakes less then or equal to provided
     * threshold and replaces with provided value. <br>
     * Number of mistakes calculated as Levenstein distance.
     *
     * @param haystack
     * @param needle
     * @param maxMistakesCount
     * @param newValue
     * @return
     */
    public static String replace(String haystack, String needle, int maxMistakesCount, String newValue) {
        BitapResult result = find(haystack, needle, maxMistakesCount);
        if (result == null) {
            // No matches found - nothing to replace
            return haystack;
        }
        return haystack.replace(result.getValue(), newValue);
    }


    /**
     * Finds provided string (needle) in haystack with number of mistakes less then or equal to provided
     * threshold and replaces with provided value. <br>
     * Number of mistakes calculated as Levenstein distance.<br>
     * <b>IMPORTANT:</b> Maximum allowed needle length is limited 15. Maximum number of mistakes depends on
     * needle length
     *
     * @param haystack
     * @param needle
     * @param maxMistakesCount
     * @param newValue
     * @return
     * @throws BitapException if provided needle is to long
     */
    public static String replacePostProcess(String haystack, String needle, int maxMistakesCount, String newValue) throws BitapException {
        if (!isAllowedNeedleLength(needle)) {
            throw new BitapException("Bitap: search string is to long, maximum allowed length=" + MAX_ALLOWED_NEEDLE_LENGTH);
        }
        int normalizedMistakesCount = normalizeThreshold(needle.length(), maxMistakesCount);
        BitapResult result = findPostProcess(haystack, needle, normalizedMistakesCount);
        if (result == null) {
            // No matches found - nothing to replace
            return haystack;
        }
        return haystack.replace(result.getValue(), newValue);
    }


    /**
     * Checks and fixes provided number of mistakes against needle length, limiting maximum number of errors
     *
     * @param needleLength length of searched string
     * @param maxMistakesCount provided number of mistakes
     * @return updated number of mistakes
     */
    static int normalizeThreshold(int needleLength, int maxMistakesCount) {
        if (needleLength <= 3) {
            // Allow no mistakes
            return 0;
        } else if (needleLength <= 5) {
            // Allow 1 mistake maximum
            return Math.min(1, maxMistakesCount);
        } else if (needleLength <= 7) {
            // Allow 2 mistakes maximum
            return Math.min(2, maxMistakesCount);
        } else if (needleLength <= 10) {
            // Allow 3 mistakes maximum
            return Math.min(3, maxMistakesCount);
        } else if (needleLength <= 15) {
            // Allow 5 mistakes maximum
            return Math.min(5, maxMistakesCount);
        }
        return 0;
    }


    /**
     * Checks, if provided needle is of allowed length to avoid high-load search queries from PostProcessing scripts
     *
     * @param needle
     * @return
     */
    static boolean isAllowedNeedleLength(String needle) {
        return needle.length() <= MAX_ALLOWED_NEEDLE_LENGTH;
    }


    /**
     * Wu-Manber algorithm.Finds all approximate (within a given Levenshtein distance) matches of a needle within a haystack.
     *
     * @param haystack - String, that will be used to search substring in
     * @param needle - Substring (Regex) to be matched
     * @param maxMistakesCount - Threshold to be used to treat match as successful
     * @return List, containing the positions where a valid substring match can start
     */
    private BitapResult findInner(String haystack, String needle, int maxMistakesCount) {
        _needleLen = needle.length();
        generateAlphabetMasks(needle);
        haystack = haystack + "&";  // sentinel value

        List<Long> bitArray = generateBitArray(maxMistakesCount);

        List<Long> old = new ArrayList<>(bitArray);
        checkSingleOperation(bitArray, 1, haystack.length() - 1, old, haystack, 0, maxMistakesCount);

        if (_locatedPositions.isEmpty()) {
            return null;
        }

        BitapResult bestResult = _locatedPositions.get(0);
        for (BitapResult result : _locatedPositions) {
            if (result.getDistance() < bestResult.getDistance()) {
                bestResult = result;
            }
        }

        return bestResult;
    }


    /**
     * Fills array of predefined character classes for use with Bitap.<br>
     * Currently supported character classes:<br>
     * <ul>
     * <li><b>\d</b> - digits, [0-9]</li>
     * <li><b>\D</b> - non-digits, [^0-9]</li>
     * <li><b>\x</b> - letter characters, [a-zA-Z]</li>
     * </ul>
     */
    private static void fillPredefinedClasses() {
        // \d = a digit, [0-9]
        List<Character> digits = new ArrayList<>();
        for (Character c = '0'; c <= '9'; c++) {
            digits.add(c);
        }
        CHARACTER_CLASSES.put('d', digits);

        // \D - non-digits
        List<Character> nonDigits = new ArrayList<>();
        for (Character c = Character.MIN_VALUE; c < Character.MAX_VALUE; c++) {
            nonDigits.add(c);
        }
        nonDigits.removeAll(digits);
        CHARACTER_CLASSES.put('D', nonDigits);

        // \x - letter characters
        List<Character> letters = new ArrayList<>();
        for (Character c = 'a'; c < 'z'; c++) {
            letters.add(c);
        }
        for (Character c = 'A'; c < 'Z'; c++) {
            letters.add(c);
        }
        CHARACTER_CLASSES.put('x', letters);
    }


    /**
     * Calculates actual needle length, taking into account character groups, defined for BITAP implementation
     *
     * @param needle
     * @return
     */
    public static int getNeedleLen(String needle) {
        int needleLen = needle.length();

        for (int i = 0; i < needle.length(); i++) {
            Character c = needle.charAt(i);
            if (c.equals('\\')) {
                Character groupKey = needle.charAt(i + 1);
                if (CHARACTER_CLASSES.containsKey(groupKey)) {
                    needleLen--;
                    i++;
                } else if (groupKey.equals('\\')) {
                    needleLen--;
                    i++;
                }
            }
        }

        return needleLen;
    }


    /**
     * Initialize the alphabet masks, one for each character of the alphabet.
     * Each alphabet mask is the length of the needle, plus a zero at the
     * right-most position. Aside from this zero, other zeroes mark locations
     * where the corresponding letter appears. For example, if the needle were
     * "Mississippi", the alphabet masks would be:
     *
     * M i s s i s s i p p i
     *
     * M : 0 1 1 1 1 1 1 1 1 1 1 0
     * i : 1 0 1 1 0 1 1 0 1 1 0 0
     * s : 1 1 0 0 1 0 0 1 1 1 1 0
     * p : 1 1 1 1 1 1 1 1 0 0 1 0
     *
     */
    private void generateAlphabetMasks(String regex) {
        _alphabetMasks = new HashMap<>();
        for (int i = 0; i < regex.length(); i++) {
            Character c = regex.charAt(i);
            if (c.equals('\\')) {
                Character groupKey = regex.charAt(i + 1);
                if (CHARACTER_CLASSES.containsKey(groupKey)) {
                    List<Character> acceptableChars = CHARACTER_CLASSES.get(groupKey);
                    acceptableChars.forEach(currChar -> {
                        markCharacterAcceptablePositions(regex, currChar);
                    });
                    _needleLen--;
                    i++;
                    continue;
                } else if (groupKey.equals('\\')) {
                    markCharacterAcceptablePositions(regex, '\\');
                    _needleLen--;
                    i++;
                    continue;
                }
            }
            markCharacterAcceptablePositions(regex, c);
        }
    }


    /**
     * Creates mask for provided character, marking as 0 positions in regex, where this character
     * can be located
     *
     * @param regex
     * @param c
     */
    private void markCharacterAcceptablePositions(String regex, Character c) {
        long mask = ~0;
        int posAdjustment = 0;
        for (int pos = 0; pos < regex.length(); pos++) {
            Character currChar = regex.charAt(regex.length() - 1 - pos);
            if (pos != regex.length() - 1) {
                Character prevChar = regex.charAt(regex.length() - 1 - pos - 1);
                if (prevChar.equals('\\')) {
                    Character groupKey = currChar;
                    if (CHARACTER_CLASSES.containsKey(groupKey)) {
                        List<Character> acceptableChars = CHARACTER_CLASSES.get(groupKey);
                        if (acceptableChars.contains(c)) {
                            mask &= ~(1L << pos - posAdjustment);
                        }
                        pos++;
                        posAdjustment++;
                        continue;
                    } else if (groupKey.equals('\\') && c.equals('\\')) {
                        mask &= ~(1L << pos - posAdjustment);
                        pos++;
                        posAdjustment++;
                        continue;
                    }
                }
            }
            if (c.equals(currChar)) {
                mask &= ~(1L << pos - posAdjustment);
            }
        }
        _alphabetMasks.put(c, (mask << 1));
    }


    /**
     * The starting bit array, commonly denoted as 'R' in the literature.
     * Commonly thought of as a 2D matrix with dimensions
     * max-Levenshtein-distance by needle-length, with the top-most row
     * corresponding to a Levenshtein distance of 0, and the bottom-most
     * corresponding to a distance of k. Initialized as all-ones, except
     * for the right-most column, which is all-zeroes. This array is
     * updated dynamically as the algorithm progresses through the search
     * corpus. This implementation is just a 1D array of longs, where each
     * long, in binary, functions as a row of the matrix. Also, since
     * longs are 64-bit, the extraneous columns on the left are just all-
     * ones.
     *
     * An example with a max-Levenshtein distance of two:
     *
     * 1 1 1 1 1 1 1 1 1 1 ... 1 1 1 1 1 1 1 1 1 0
     * 1 1 1 1 1 1 1 1 1 1 ... 1 1 1 1 1 1 1 1 1 0
     * 1 1 1 1 1 1 1 1 1 1 ... 1 1 1 1 1 1 1 1 1 0
     * |<---------------- 64-bits ---------------->|
     *
     * @param lev - the maximum Levenshtein distance for a substring match
     * @return the starting bit array
     */
    private List<Long> generateBitArray(int maxMistakesCount) {
        List<Long> bitArray = new ArrayList<>();
        for (int k = 0; k <= maxMistakesCount; k++) {
            bitArray.add(Long.valueOf(~1));
        }
        return bitArray;
    }


    /**
     * Returns character mask for provided character
     *
     * @param c
     * @return
     */
    private long getMaskForChar(Character c) {
        if (_alphabetMasks.containsKey(c)) {
            return _alphabetMasks.get(c);
        } else {
            return ~0 << 1;
        }
    }


    /**
     * Recursive function, that checks haystack symbol-by-symbol in tree-like approach
     *
     * @param bitArray
     * @param step
     * @param offset
     * @param old
     * @param haystack
     * @param lenghtOffset
     * @param maxMistakesCount
     */
    private void checkSingleOperation(List<Long> bitArray, int step, int offset, List<Long> old, String haystack, int lenghtOffset, int maxMistakesCount) {
        if (offset >= 0) {
            bitArray.set(step - 1, (old.get(step - 1) << 1) | getMaskForChar(haystack.charAt(offset)));
            if (0 == (bitArray.get(step - 1) & (1 << _needleLen))) {
                addLocatedResult(new BitapResult(offset, step - 1, haystack.substring(offset, offset + _needleLen + lenghtOffset)));
            }
        } else {
            bitArray.set(step - 1, (old.get(step - 1)));
            if (0 == (bitArray.get(step - 1) & (1 << _needleLen))) {
                addLocatedResult(new BitapResult(0, step - 1, haystack.substring(0, _needleLen + lenghtOffset)));
            }
        }

        if (step <= maxMistakesCount && offset >= 0) {
            long ins = old.get(step - 1);
            long sub = ins << 1;
            long del = bitArray.get(step - 1) << 1;
            long match = (old.get(step - 1) << 1) | getMaskForChar(haystack.charAt(offset));

            List<Long> newBitArray;
            List<Long> newOld;
            offset--;

            // Suppose extra character at current position
            newBitArray = new ArrayList<>(bitArray);
            newOld = new ArrayList<>(newBitArray);
            newOld.set(step, ins);
            checkSingleOperation(newBitArray, step + 1, offset, newOld, haystack, lenghtOffset + 1, maxMistakesCount);

            // Suppose changed character at current position
            if (offset != haystack.length() - 2) {
                // If this is first step - do not check, if string will be found, if we alter it. First character is sentinel
                // value. If there's matches with 'missing' characters in the end of haystack - they will be found in other check
                newBitArray = new ArrayList<>(bitArray);
                newOld = new ArrayList<>(newBitArray);
                newOld.set(step, sub);
                checkSingleOperation(newBitArray, step + 1, offset, newOld, haystack, lenghtOffset, maxMistakesCount);
            }

            // Suppose missing character at current position
            newBitArray = new ArrayList<>(bitArray);
            newOld = new ArrayList<>(newBitArray);
            newOld.set(step, del);
            checkSingleOperation(newBitArray, step + 1, offset, newOld, haystack, lenghtOffset - 1, maxMistakesCount);

            // Suppose valid character at current position
            newBitArray = new ArrayList<>(bitArray);
            newOld = new ArrayList<>(newBitArray);
            newOld.set(step - 1, match);
            checkSingleOperation(newBitArray, step, offset, newOld, haystack, lenghtOffset, maxMistakesCount);
        } else if (offset >= 0) {
            // Maximum number of mistakes reached, string not found, but still some text left
            long match = (old.get(step - 1) << 1) | getMaskForChar(haystack.charAt(offset));
            List<Long> newBitArray;
            List<Long> newOld;
            offset--;

            newBitArray = new ArrayList<>(bitArray);
            newOld = new ArrayList<>(newBitArray);
            newOld.set(step - 1, match);
            checkSingleOperation(newBitArray, step, offset, newOld, haystack, lenghtOffset, maxMistakesCount);
        } else if (step <= maxMistakesCount) {
            // Haystack start reached, string not found, but still there can be some errors made - check for 'missing characters'
            long del = bitArray.get(step - 1) << 1;
            List<Long> newBitArray;
            List<Long> newOld;
            offset--;

            newBitArray = new ArrayList<>(bitArray);
            newOld = new ArrayList<>(newBitArray);
            newOld.set(step, del);
            checkSingleOperation(newBitArray, step + 1, offset, newOld, haystack, lenghtOffset - 1, maxMistakesCount);
        }
    }


    /**
     * Checks, if results list already contains provided result, and adds it to the list, if it was not
     * found
     *
     * @param result
     */
    private void addLocatedResult(BitapResult result) {
        if (!_locatedPositions.contains(result)) {
            _locatedPositions.add(result);
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Holds information about single Bitap matching result
     */
    public class BitapResult {

        private int _position;
        private double _distance;
        private String _value;


        public BitapResult(int position, double distance, String value) {
            _position = position;
            _distance = distance;
            _value = value;
        }


        public int getPosition() {
            return _position;
        }


        public void setPosition(int position) {
            _position = position;
        }


        public double getDistance() {
            return _distance;
        }


        public void setDistance(double distance) {
            _distance = distance;
        }


        public String getValue() {
            return _value;
        }


        public void setValue(String value) {
            _value = value;
        }


        public int getConfidence() {
            return (int) (((double) (_needleLen - _distance)) / ((double) _needleLen) * 100);
        }


        @Override
        public int hashCode() {
            int hash = 7;
            hash = 79 * hash + this._position;
            hash = 79 * hash + (int) (Double.doubleToLongBits(this._distance) ^ (Double.doubleToLongBits(this._distance) >>> 32));
            hash = 79 * hash + Objects.hashCode(this._value);
            return hash;
        }


        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final BitapResult other = (BitapResult) obj;
            if (this._position != other._position) {
                return false;
            }
            if (Double.doubleToLongBits(this._distance) != Double.doubleToLongBits(other._distance)) {
                return false;
            }
            if (!Objects.equals(this._value, other._value)) {
                return false;
            }
            return true;
        }

    }


    /**
     * Holds information about single 'substring' of original search string, that will be used during
     * Bitap matching
     */
    protected static class BitapTextPosition {

        private final String _text;
        private final int _position;


        public BitapTextPosition(String text, int position) {
            _text = text;
            _position = position;
        }


        public String getText() {
            return _text;
        }


        public int getPosition() {
            return _position;
        }


        @Override
        public int hashCode() {
            int hash = 5;
            hash = 97 * hash + Objects.hashCode(this._text);
            hash = 97 * hash + this._position;
            return hash;
        }


        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final BitapTextPosition other = (BitapTextPosition) obj;
            if (this._position != other._position) {
                return false;
            }
            if (!Objects.equals(this._text, other._text)) {
                return false;
            }
            return true;
        }

    }

}
