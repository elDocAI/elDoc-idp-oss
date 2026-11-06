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
package ai.eldoc.ocr;

import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.util.RectangleUtils;
import com.ibm.icu.text.Bidi;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;
import java.util.*;
import java.util.stream.Collectors;
import static com.ibm.icu.text.Bidi.OPTION_REMOVE_CONTROLS;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * OcrText Utils
 */
public class OcrTextUtils {

    /**
     * Minimum text similarity required to treat header or footer on different page as same
     */
    private static final double HEADER_FOOTER_SIMILARITY_THRESHOLD = 0.9;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private OcrTextUtils() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Builds table-like structure of provided text, where rows correspond to text lines and it's content
     * is grouped into the words
     *
     * @param text
     * @return
     */
    public static List<List<OcrText>> mergeWordsAndFindRows(OcrText text) {
        return findRows(text).stream()
                .map(OcrTextUtils::findWords)
                .collect(Collectors.toList());
    }


    /**
     * Groups provided text into lines according to symbol's Y coordinate
     *
     * @param text
     * @return
     */
    public static List<OcrText> findRows(OcrText text) {
        List<OcrText> rowsList = new ArrayList<>();
        List<Range<Double>> rowRangeList = new ArrayList<>();

        // 1. Sort rectangles by Y coordinate to ensure located rows will be already sorted
        List<OcrSymbol> symbolListSorted = text.getOcrData().stream()
                .sorted(Comparator.comparingDouble(symb -> symb.getRegion().getY()))
                .collect(Collectors.toList());

        // 2. Find rows in provided rectangle list
        for (OcrSymbol symb : symbolListSorted) {
            boolean rowFound = false;
            Range<Double> rectRange = RectangleUtils.getRangeY(symb.getRegion());
            for (int i = 0; i < rowsList.size(); i++) {
                Range<Double> rowRange = rowRangeList.get(i);
                if (!rowRange.isOverlappedBy(rectRange)) {
                    continue;
                }
                Range<Double> intersection = rowRange.intersectionWith(rectRange);
                if (intersection.getMaximum() - intersection.getMinimum() > symb.getRegion().getHeight() * 0.4) {
                    rowsList.get(i).addSymbol(symb);

                    // Calculate new range for located row
                    rowRangeList.set(i, rectRange);

                    rowFound = true;
                    break;
                }
            }

            // If no row found, that intersects current rectangle - add new row with current rectangle in it
            if (!rowFound) {
                OcrText newRow = new OcrText();
                newRow.addSymbol(symb);
                rowsList.add(newRow);
                rowRangeList.add(rectRange);
            }
        }

        return rowsList;
    }


    /**
     * Groups provided text into words. Two consecutive symbols treated as belonging to different words, if they have
     * whitespace character between them or their distance is bigger, than average symbol width of currently processed word
     *
     * @param text
     * @return
     */
    public static List<OcrText> findWords(OcrText text) {
        List<OcrText> wordsList = new ArrayList<>();

        // 1. Copy initial list of rectangles
        List<OcrSymbol> symbolListSorted = new ArrayList<>(text.getOcrData());

        // 2. Sort rectangles by X coordinate to ensure located words will be already sorted
        symbolListSorted.sort(Comparator.comparingDouble(symb -> symb.getRegion().getX()));

        OcrText word = new OcrText();
        double lastSymbolX = symbolListSorted.get(0).getRegion().getX();
        for (OcrSymbol symbol : symbolListSorted) {
            if (Character.isWhitespace(symbol.getChar()) || getAvgCharWidth(word) < symbol.getRegion().getX() - lastSymbolX) {
                if (isNotBlank(word.getText())) {
                    // In some cases, it is possible to have multiple whitespace characters in a row,
                    // resulting in 'blank' words - such words should be skipped
                    word.setRtl(isWordRtl(word));
                    word.setRegion(RectangleUtils.union(word.getOcrData().stream().map(OcrSymbol::getRegion).collect(Collectors.toList())));
                    wordsList.add(word);
                }
                word = new OcrText();
            }
            word.addSymbol(symbol);
            lastSymbolX = symbol.getRegion().getMaxX();
        }

        if (!wordsList.contains(word) && !isBlank(word.getText())) {
            // Last word was not yet added to the list
            word.setRtl(isWordRtl(word));
            word.setRegion(RectangleUtils.union(word.getOcrData().stream().map(OcrSymbol::getRegion).collect(Collectors.toList())));
            wordsList.add(word);
        }

        return wordsList;
    }


    /**
     * Calculates average character's width in provided {@link OcrText}
     *
     * @param text
     * @return
     */
    public static double getAvgCharWidth(OcrText text) {
        return text.getOcrData().parallelStream().mapToDouble(symb -> symb.getRegion().getWidth()).average().orElse(0);
    }


    /**
     * Calculates average font size in a list of {@link OcrText} elements
     *
     * @param text
     * @return
     */
    public static double getAvgFontSize(List<OcrText> text) {
        return text.parallelStream().mapToDouble(OcrText::getAvgFontSize).average().orElse(0);
    }


    /**
     * Sorts symbols in human-readable form, i.e. top to bottom, left to right, by finding 'rows' in provided list
     * of rectangles, and then sorting symbols in each of them
     * <b>IMPORTANT:</b> Adds space symbol in the end of each row to avoid merging words (i.e. instead of newline)
     *
     * @param rectList
     * @return
     */
    public static List<OcrSymbol> sortSymbols(List<OcrSymbol> rectList) {
        List<List<OcrSymbol>> table = new ArrayList<>();
        List<Range<Double>> rowRangeList = new ArrayList<>();

        // 1. Copy initial list of rectangles
        List<OcrSymbol> rectListSorted = new ArrayList<>(rectList);

        // 2. Sort rectangles by Y coordinate to ensure located rows will be already sorted
        rectListSorted.sort(Comparator.comparingDouble(symb -> symb.getRegion().getY()));

        // 3. Find rows in provided rectangle list
        for (OcrSymbol symb : rectListSorted) {
            boolean rowFound = false;
            Range<Double> rectRange = RectangleUtils.getRangeY(symb.getRegion());
            for (int i = 0; i < table.size(); i++) {
                Range<Double> rowRange = rowRangeList.get(i);
                if (!rowRange.isOverlappedBy(rectRange)) {
                    continue;
                }
                Range<Double> intersection = rowRange.intersectionWith(rectRange);
                if (intersection.getMaximum() - intersection.getMinimum() > symb.getRegion().getHeight() * 0.5) {
                    table.get(i).add(symb);

                    // Calculate new range for located row
                    double newRangeStart = Math.min(rowRange.getMinimum(), rectRange.getMinimum());
                    double newRangeEnd = Math.max(rowRange.getMaximum(), rectRange.getMaximum());
                    rowRangeList.set(i, Range.of(newRangeStart, newRangeEnd));

                    rowFound = true;
                    break;
                }
            }

            // If no row found, that intersects current rectangle - add new row with current rectangle in it
            if (!rowFound) {
                List<OcrSymbol> newRow = new ArrayList<>();
                newRow.add(symb);
                table.add(newRow);
                rowRangeList.add(rectRange);
            }
        }

        // 4. Sort rectangles in each row by X coordinate
        table.forEach(row -> {
            // Retrieve groups of symbols, that share same text order (left-to-right or right-to-left)
            List<List<OcrSymbol>> groups = getTextOrientationGroups(row);

            // Sort text in each group according to it's text orientation
            groups.forEach(OcrTextUtils::sortSymbolGroup);

            row.clear();

            if (OcrTextUtils.isBlockRtl(groups)) {
                groups.stream()
                        .sorted(Comparator.comparingDouble(g -> ((List<OcrSymbol>) g).get(0).getRegion().getX()).reversed())
                        .flatMap(List::stream)
                        .forEach(row::add);
            } else {
                groups.stream()
                        .flatMap(List::stream)
                        .forEach(row::add);
            }

            // Add space character in the end of each row
            OcrSymbol lastSymbolInRow = row.get(row.size() - 1);
            if (!Character.isWhitespace(lastSymbolInRow.getChar())) {
                OcrSymbol spaceSymbol = new OcrSymbol();
                spaceSymbol.setChar(' ');
                spaceSymbol.setConfidence(100);
                spaceSymbol.setFontSizePt(lastSymbolInRow.getFontSizePt());
                spaceSymbol.setRegion(lastSymbolInRow.getRegion().clone());
                spaceSymbol.getRegion().translate(lastSymbolInRow.getRegion().getWidth(), 0); // Move space to start right after last symbol
                row.add(spaceSymbol);
            }
        });

        // 5. Merge rows and return obtained list
        return table.stream().flatMap(row -> row.stream()).collect(Collectors.toList());
    }


    /**
     * Sorts symbols inside group, depending on it's text orientation.<br>
     * <b>IMPORTANT:</b> text orientation is checked from first character only, as it is supposed, that
     * single text area ("word") should have same text orientation across all characters.
     *
     * @param group
     */
    private static void sortSymbolGroup(List<OcrSymbol> group) {
        if (group.isEmpty()) {
            return;
        }

        if (group.get(0).isRtl()) {
            group.sort(Comparator.comparingDouble(symb -> getSymbolXCoordForSorting((OcrSymbol) symb)).reversed());
        } else {
            group.sort(Comparator.comparingDouble(symb -> getSymbolXCoordForSorting((OcrSymbol) symb)));
        }
    }


    /**
     * Tries to find groups of consequent characters in a text line, that share same text orientation
     * (runs in terms of {@link java.text.Bidi})
     *
     * @param text List of OcrSymbols (characters), that represents processed row
     * @return
     */
    protected static List<List<OcrSymbol>> getTextOrientationGroups(List<OcrSymbol> text) {
        List<OcrSymbol> sortedText = text.stream()
                .sorted(Comparator.comparingDouble(symb -> symb.getRegion().getX() + symb.getRegion().getWidth() / 3))
                .collect(Collectors.toList());

        List<List<OcrSymbol>> result = new ArrayList<>();
        boolean prevRtlState = false;
        List<OcrSymbol> currSymbolsList = new ArrayList<>();
        result.add(currSymbolsList);
        for (OcrSymbol symb : sortedText) {
            if (symb.isRtl() != prevRtlState) {
                if (currSymbolsList.isEmpty()) {
                    result.remove(currSymbolsList);
                }
                currSymbolsList = new ArrayList<>();
                result.add(currSymbolsList);
                prevRtlState = symb.isRtl();
            }
            currSymbolsList.add(symb);
        }
        return result;
    }


    /**
     * Returns adjusted X coordinate, that should be used during text symbols sorting.<br>
     * This method takes into account characters direction (rtl/ltr) and adjusts character position by 1/3 of character's width.
     *
     * @param symbol
     * @return
     */
    protected static double getSymbolXCoordForSorting(OcrSymbol symbol) {
        if (symbol.isRtl()) {
            return symbol.getRegion().getMaxX() - symbol.getRegion().getWidth() / 3;
        } else {
            return symbol.getRegion().getX() + symbol.getRegion().getWidth() / 3;
        }
    }


    private static boolean isWordRtl(OcrText word) {
        return isBlockRtl(List.of(word.getOcrData()));
    }


    /**
     * Checks, if specified block (in PDF terms, paragraph or cell) is left-to-right of right-to-left
     * oriented from the statistics of RTL flags in OcrSymbols.<br>
     * This method is not guaranteed to return valid results, as for example, extracted text area can
     * contain "1111111111111111111 بيان", in which case technically the block is right-to-left oriented
     * (this is Arabic text), but there is more left-to-right oriented characters.
     *
     * @param block
     * @return
     */
    private static boolean isBlockRtl(List<List<OcrSymbol>> block) {
        long rtlSymbols = block.stream()
                .flatMap(List::stream)
                .filter(symbol -> symbol.isRtlPara())
                .count();
        long totalSymbols = block.stream()
                .flatMap(List::stream)
                .count();

        return rtlSymbols > totalSymbols / 2;
    }


    public static boolean isParaRtl(List<OcrText> paraText) {
        if (paraText.stream().map(OcrText::getOcrData).flatMap(List::stream).allMatch(OcrSymbol::isRtlPara)) {
            // All text in paragraph is RTL
            return true;
        } else if (paraText.stream().map(OcrText::getOcrData).flatMap(List::stream).noneMatch(OcrSymbol::isRtlPara)) {
            // All text in paragraph is LTR
            return false;
        } else {
            // This should not happen, as text should be processed on per-paragraph basis (or per-line, but text line always belongs to same paragraph)
            long rtlCharsCount = paraText.stream()
                    .map(OcrText::getOcrData)
                    .flatMap(List::stream)
                    .map(OcrSymbol::isRtlPara)
                    .filter(rtl -> rtl == true)
                    .count();
            return rtlCharsCount > paraText.size() / 2;
        }
    }


    /**
     * Creates String, that matches 'visual' orientation of the text.<br>
     * For example, for text in Right-to-Left languages, 'logical' order is the same, but 'visual' of the symbols will be reversed.<br>
     * Uses BiDi for building resulting string.<br>
     *
     * @param text Source OcrBlock
     * @param updateFlags If true, additional analysis will be performed and resulting Rtl flags will be set according to BiDi analysis.
     * Otherwise, they will be copied from source text.
     * @return New OcrBlock instance with direction applied to each word
     */
    public static OcrBlock bidiReorderToVisual(OcrBlock text, boolean updateFlags) {
        OcrBlock result = new OcrBlock();

        Bidi bidi = new Bidi(0, 0); // https://unicode-org.github.io/icu-docs/apidoc/dev/icu4j/com/ibm/icu/text/Bidi.html
        bidi.setReorderingOptions(OPTION_REMOVE_CONTROLS);

        for (OcrText word : text.getOcrData()) {
            bidi.setPara(word.getText(), (byte) (word.isRtl() ? Bidi.DIRECTION_RIGHT_TO_LEFT : Bidi.DIRECTION_LEFT_TO_RIGHT), null);
            String wordStr = bidi.writeReordered(Bidi.DO_MIRRORING);
            int[] charactersMap = bidi.getVisualMap();

            // Transfer characteristics, that do not rely on symbols ordering
            OcrText resultWord = word.clone();

            resultWord.setText(wordStr);
            resultWord.getOcrData().clear();

            if (!word.getOcrData().isEmpty()) {
                for (int i = 0; i < wordStr.length(); i++) {
                    if (charactersMap[i] == -1) {
                        // Current character maps to nowhere, according to BiDi - it is missing in source text
                        continue;
                    }
                    OcrSymbol sourceSymbolCopy = word.getOcrData().get(charactersMap[i]).clone();
                    if (updateFlags) {
                        sourceSymbolCopy.setRtl(bidi.getLevelAt(i) == 1);
                        sourceSymbolCopy.setRtlPara(bidi.getLevelAt(i) == 1);
                    }
                    resultWord.getOcrData().add(sourceSymbolCopy);
                }
            }

            if (updateFlags) {
                resultWord.setRtl(isWordRtl(resultWord));
                resultWord.setRtlPara(resultWord.isRtl());
                // Update per-symbol RtlPara flag for consistency
                resultWord.getOcrData().forEach(s -> s.setRtlPara(resultWord.isRtlPara()));
                resultWord.getOcrData().forEach(s -> s.setRtl(resultWord.isRtl()));
            }

            result.getOcrData().add(resultWord);
        }

        return result;
    }


    /**
     * Finds header on document
     *
     * @param documentPagesText Text for all document pages
     * @return Pair of header end on first page and other pages
     */
    public static Pair<Double, Double> findHeader(List<OcrText> documentPagesText) {
        return findHeaderFooter(documentPagesText, true);
    }


    /**
     * Finds footer on document
     *
     * @param documentPagesText Text for all document pages
     * @return Pair of footer start on first page and other pages
     */
    public static Pair<Double, Double> findFooter(List<OcrText> documentPagesText) {
        return findHeaderFooter(documentPagesText, false);
    }


    /**
     * Finds header or footer on document
     *
     * @param documentPagesText Text for all document pages
     * @param findHeader If {@code true} - finds header configuration, otherwise - footer
     * @return Pair of height (end of header or start of footer) of first page and other pages
     */
    private static Pair<Double, Double> findHeaderFooter(List<OcrText> documentPagesText, boolean findHeader) {
        if (documentPagesText.size() < 2) {
            return Pair.of(findHeader ? 0 : Double.MAX_VALUE, findHeader ? 0 : Double.MAX_VALUE);
        }

        // If there is more than 3 pages in the document - skip first page at initial checks, as it may have different header/footer configuration
        boolean skipFirstPage = documentPagesText.size() > 3;

        List<List<OcrText>> documentPageTextByLines = documentPagesText.stream().map(OcrTextUtils::findRows).toList();
        int maxPossibleHeaderFooterHeight = documentPageTextByLines.stream().mapToInt(List::size).min().orElse(0);

        LevenshteinDistance tester = new LevenshteinDistance();

        int headerFooterHeight = 0;
        for (int rowCount = 1; rowCount < maxPossibleHeaderFooterHeight; rowCount++) {
            String supposedHeaderText = getHeaderFooterText(documentPageTextByLines.get(0), rowCount, findHeader);
            boolean allHeadersMatches = true;
            for (int pageIdx = skipFirstPage ? 1 : 0; pageIdx < documentPageTextByLines.size(); pageIdx++) {
                String supposedCurrentPageHeaderText = getHeaderFooterText(documentPageTextByLines.get(pageIdx), rowCount, findHeader);
                int headerSize = Math.min(supposedHeaderText.length(), supposedCurrentPageHeaderText.length());
                int distance = tester.apply(supposedHeaderText, supposedCurrentPageHeaderText);
                double similarity = 1 - ((double) distance) / ((double) headerSize);
                if (similarity < HEADER_FOOTER_SIMILARITY_THRESHOLD) {
                    allHeadersMatches = false;
                    break;
                }
            }
            if (!allHeadersMatches) {
                headerFooterHeight = rowCount - 1;
                break;
            }
        }

        if (headerFooterHeight == 0) {
            return Pair.of(findHeader ? 0 : Double.MAX_VALUE, findHeader ? 0 : Double.MAX_VALUE);
        }
        String supposedHeaderText = getHeaderFooterText(documentPageTextByLines.get(1), headerFooterHeight, findHeader);
        Rectangle supposedArea = documentPageTextByLines.get(1).stream()
                .skip(findHeader ? 0 : (documentPageTextByLines.get(1).size() - headerFooterHeight))
                .limit(findHeader ? headerFooterHeight : Integer.MAX_VALUE).map(OcrText::getTextArea)
                .collect(Collectors.collectingAndThen(Collectors.toList(), RectangleUtils::union));
        Double supposedHeaderFooterBorder = findHeader ? supposedArea.getMaxY() : supposedArea.getY();
        String firstPageHeaderText = getHeaderFooterText(documentPageTextByLines.get(0), headerFooterHeight, findHeader);

        boolean firstPageHeaderSame = tester.apply(supposedHeaderText, firstPageHeaderText) >= HEADER_FOOTER_SIMILARITY_THRESHOLD;
        Rectangle supposedFirstPageArea = documentPageTextByLines.get(1).stream()
                .skip(findHeader ? 0 : (documentPageTextByLines.get(1).size() - headerFooterHeight))
                .limit(findHeader ? headerFooterHeight : Integer.MAX_VALUE).map(OcrText::getTextArea)
                .collect(Collectors.collectingAndThen(Collectors.toList(), RectangleUtils::union));
        Double supposedFirstPageHeaderFooterBorder = findHeader ? supposedFirstPageArea.getMaxY() : supposedFirstPageArea.getY();

        return Pair.of((firstPageHeaderSame ? supposedFirstPageHeaderFooterBorder : (findHeader ? 0.0 : Double.MAX_VALUE)), supposedHeaderFooterBorder);
    }


    /**
     * Returns text of first or last N lines of text
     *
     * @param sourceText Source text to be processed
     * @param rowsCount Number of rows to be extracted
     * @param header if {@code true} - extracts first lines (header), otherwise last lines (footer) are extracted
     * @return
     */
    private static String getHeaderFooterText(List<OcrText> sourceText, int rowsCount, boolean header) {
        return sourceText.stream().skip(header ? 0 : (sourceText.size() - rowsCount)).limit(header ? rowsCount : Integer.MAX_VALUE).map(OcrText::getText).collect(Collectors.joining(" "));
    }

}
