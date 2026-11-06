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
package ai.eldoc.util;

import ai.eldoc.model.reco.Rectangle;
import org.apache.commons.lang3.Range;
import org.apache.commons.math3.ml.clustering.*;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.random.AbstractRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;


/**
 * Utility class with helper methods for {@link Rectangle}
 */
public class RectangleUtils {

    private static final int KMEANS_MAX_ITERATIONS = 1000;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Builds table-like structure of rectangles, sorting them in human-readable form, i.e. top to
     * bottom, left to right, by finding 'rows' in provided list of rectangles, and them sorting
     * rows and rectangles in each of them
     *
     * @param rectList
     * @return
     */
    public static List<List<Rectangle>> sortNaturalOrder(List<Rectangle> rectList) {
        List<List<Rectangle>> table = new ArrayList<>();
        List<Range<Double>> rowRangeList = new ArrayList<>();

        // 1. Copy initial list of rectangles
        List<Rectangle> rectListSorted = new ArrayList<>(rectList);

        // 2. Sort rectangles by Y coordinate to ensure located rows will be already sorted
        rectListSorted.sort(Comparator.comparingDouble(Rectangle::getX));

        // 3. Find rows in provided rectangle list
        for (Rectangle rectangle : rectListSorted) {
            boolean rowFound = false;
            Range<Double> rectRange = getRangeY(rectangle);
            for (int i = 0; i < table.size(); i++) {
                Range<Double> rowRange = rowRangeList.get(i);
                if (!rowRange.isOverlappedBy(rectRange)) {
                    continue;
                }
                Range<Double> intersection = rowRange.intersectionWith(rectRange);
                if (intersection.getMaximum() - intersection.getMinimum() > rectangle.getHeight() * 0.4) {
                    table.get(i).add(rectangle);

                    // Calculate new range for located row
                    rowRangeList.set(i, rectRange);

                    rowFound = true;
                    break;
                }
            }

            // If no row found, that intersects current rectangle - add new row with current rectangle in it
            if (!rowFound) {
                List<Rectangle> newRow = new ArrayList<>();
                newRow.add(rectangle);
                table.add(newRow);
                rowRangeList.add(rectRange);
            }
        }

        // 4. Sort rectangles in each row by X coordinate
        table.forEach(row -> {
            row.sort(Comparator.comparingDouble(Rectangle::getX));
        });

        // 5. Sort rows
        table.sort(Comparator.comparingDouble(row -> row.get(0).getY()));

        return table;
    }


    /**
     * Builds {@link Rectangle}, that contains all rectangles from provided list, joining them one
     * by one with {@link Rectangle#union(ai.eldoc.model.reco.Rectangle)}
     *
     * @param rectangles
     * @return
     */
    public static Rectangle union(List<Rectangle> rectangles) {
        if (rectangles == null || rectangles.isEmpty()) {
            return new Rectangle();
        }

        Rectangle result = rectangles.get(0).clone();
        rectangles.forEach(result::add);
        return result;
    }


    public static List<Range<Double>> findVerticalGapGroups(List<Rectangle> rectangles, int groupsCount) {
        return getClusters(getVerticalDistanceList(rectangles), groupsCount);
    }


    public static void fillVerticalGaps(List<List<Rectangle>> rectangles) {
        DoubleAdder rowHeightAdj = new DoubleAdder();
        rowHeightAdj.add(5);
        rectangles.forEach(tableRow -> {
            Rectangle tableRowRect = RectangleUtils.union(tableRow);
            double rowY = tableRowRect.getY();
            double rowMaxY = tableRowRect.getMaxY();

            // Find next row and it's start
            List<Rectangle> nextRow = rectangles.get(rectangles.indexOf(tableRow) == rectangles.size() - 1 ? rectangles.indexOf(tableRow) : rectangles.indexOf(tableRow) + 1);
            Rectangle nextRowRect = RectangleUtils.union(nextRow);
            double nextRowY = nextRowRect.getY();

            // Adjust current row height, adding half of distance between current and previous row and current and next row
            double rowHeight = rowMaxY - rowY + (nextRow.equals(tableRow) ? ((rowMaxY - rowY) / 5) : (nextRowY - rowMaxY) / 2) + rowHeightAdj.doubleValue();

            tableRow.parallelStream().forEach(cell -> {
                cell.setY(rowY - rowHeightAdj.doubleValue());
                cell.setHeight(rowHeight);
            });

            // Remember distance to the next row
            rowHeightAdj.reset();
            rowHeightAdj.add((nextRow.equals(tableRow) ? ((rowMaxY - rowY) / 5) : (nextRowY - rowMaxY) / 2));
        });
    }


    /**
     * Builds list of 'clusters', based on provided rectangles (<b>IMPORTANT:</b> rectangles list should
     * be already sorted by Y), following next rules:
     * <ul>
     * <li>For each rectangle, calculate it's vertical distance to previous, if possible;</li>
     * <li>Remove negative distances</li>
     * <li>Group distances into clusters - element (distance) belongs to cluster, if it is smaller
     * than cluster's average element * 2</li>
     * </ul>
     *
     * @param distanceList
     * @param clustersCount
     * @return
     */
    static List<Range<Double>> getClusters(List<Double> distanceList, int clustersCount) {
        if (distanceList == null || distanceList.size() < clustersCount) {
            return new ArrayList<>();
        }

        RandomGenerator generator = new AbstractRandomGenerator() {

            private int k = 0;
            private double min = distanceList.stream().mapToDouble(i -> i).min().orElse(0);
            private double max = distanceList.stream().mapToDouble(i -> i).max().orElse(0);


            @Override
            public void setSeed(long seed) {
                return;
            }


            @Override
            public double nextDouble() {
                if (max == min) {
                    return 1.0;
                } else {
                    double result = min + (max - min) / clustersCount * k;
                    k++;
                    return (result - min) / (max - min);
                }
            }
        };

        KMeansPlusPlusClusterer<DoublePoint> dbClusterer = new KMeansPlusPlusClusterer<>(clustersCount, KMEANS_MAX_ITERATIONS, new EuclideanDistance(), generator);
        List<DoublePoint> points = distanceList.stream()
                .map(number -> new double[]{number})
                .map(DoublePoint::new)
                .collect(Collectors.toList());

        List<CentroidCluster<DoublePoint>> clusterList = dbClusterer.cluster(points);

        removeNoise(clusterList);

        return clusterList.stream()
                .filter(dbscanCluster -> !dbscanCluster.getPoints().isEmpty())
                .sorted(Comparator.comparingDouble(dbscanCluster -> dbscanCluster.getPoints().get(0).getPoint()[0]))
                .map(dbscanCluster -> dbscanCluster.getPoints().stream().map(point -> point.getPoint()[0]).collect(Collectors.toList()))
                .map(cluster -> {
                    double clusterMin = cluster.stream().min(Comparator.naturalOrder()).orElse(0.0);
                    double clusterMax = cluster.stream().max(Comparator.naturalOrder()).orElse(0.0);
                    return Range.of(clusterMin, clusterMax);
                })
                .collect(Collectors.toList());
    }


    static boolean removeNoise(List<CentroidCluster<DoublePoint>> clusterList) {
        boolean noiseRemoved = false;

        for (CentroidCluster<DoublePoint> cluster : clusterList) {
            Double centroid = cluster.getCenter().getPoint()[0];

            // Calculate average distance from cluster element to it's center
            double averageDistance = cluster.getPoints().stream().mapToDouble(element -> getDistance(centroid, element.getPoint()[0])).average().orElse(0);

            Iterator<DoublePoint> pointsIterator = cluster.getPoints().iterator();
            while (pointsIterator.hasNext()) {
                DoublePoint element = pointsIterator.next();
                double elementDistance = getDistance(centroid, element.getPoint()[0]);
                if (elementDistance > Math.ceil(averageDistance * 1.5)) {
                    // ELement is located to far from the center - most probably it is 'noise'
                    pointsIterator.remove();
                    noiseRemoved = true;
                }
            }
        }

        return noiseRemoved;
    }


    /**
     * Returns list of vertical distances between rectangles, measuring distance between Rectangle1 and
     * Rectangle 2 as the distance between top border of lower rectangle and bottom border of higher rectangle
     *
     * @param rectangles
     * @return
     */
    static List<Double> getVerticalDistanceList(List<Rectangle> rectangles) {
        if (rectangles == null || rectangles.isEmpty()) {
            // Provided list of rectangles is empty
            return new ArrayList<>();
        }

        if (rectangles.size() == 1) {
            // Provided list of rectangles contain only one Rectangle, no distances to be calculated
            return new ArrayList<>();
        }

        // 1. Copy initial list of rectangles
        List<Rectangle> rectListSorted = new ArrayList<>(rectangles);

        // 2. Sort rectangles by Y coordinate to ensure located rows will be already sorted
        rectListSorted.sort(Comparator.comparingDouble(Rectangle::getY));

        // 3. Calculate distances
        List<Double> distances = new ArrayList<>();

        Rectangle previousRect = rectListSorted.get(0);
        for (int i = 1; i < rectListSorted.size(); i++) {
            Rectangle currentRect = rectListSorted.get(i);
            distances.add(getVerticalDistance(previousRect, currentRect));
            previousRect = currentRect;
        }

        return distances;
    }


    /**
     * Returns list of horizontal distances between rectangles, measuring distance between Rectangle 1 and
     * Rectangle 2 as the distance between the right border of the left rectangle to the left border of the
     * right rectangle
     *
     * @param rectangles
     * @return
     */
    static List<Double> getHorizontalDistanceList(List<Rectangle> rectangles) {
        if (rectangles == null || rectangles.isEmpty()) {
            // Provided list of rectangles is empty
            return new ArrayList<>();
        }

        if (rectangles.size() == 1) {
            // Provided list of rectangles contain only one Rectangle, no distances to be calculated
            return new ArrayList<>();
        }

        // 1. Copy initial list of rectangles
        List<Rectangle> rectListSorted = new ArrayList<>(rectangles);

        // 2. Sort rectangles by Y coordinate to ensure located rows will be already sorted
        rectListSorted.sort(Comparator.comparingDouble(Rectangle::getX));

        // 3. Calculate distances
        List<Double> distances = new ArrayList<>();

        Rectangle previousRect = rectListSorted.get(0);
        for (int i = 1; i < rectListSorted.size(); i++) {
            Rectangle currentRect = rectListSorted.get(i);
            distances.add(getHorizontalDistance(previousRect, currentRect));
            previousRect = currentRect;
        }

        return distances;
    }


    /**
     * Calculates distance between two rectangles.<br>
     * <b>IMPORTANT:</b> This methods takes into account rectangles relative position, meaning
     * returned distance is a distance from lower border of the rectangle, located higher, to
     * upper border of the rectangle, located lower.
     *
     * @param first
     * @param second
     * @return
     */
    public static double getVerticalDistance(Rectangle first, Rectangle second) {
        if (first.getY() < second.getY()) {
            // Second rectangle located 'under' first rectangle
            return second.getY() - first.getMaxY();
        } else {
            // First rectangle located 'under' second rectangle
            return first.getY() - second.getMaxY();
        }
    }


    /**
     * Calculates distance between two rectangles.<br>
     * <b>IMPORTANT:</b> This methods takes into account rectangles relative position, meaning
     * returned distance is a distance from right border of the left rectangle, to
     * the left border of the right rectangle.
     *
     * @param first
     * @param second
     * @return
     */
    public static double getHorizontalDistance(Rectangle first, Rectangle second) {
        if (first.getX() < second.getX()) {
            // Second rectangle located 'under' first rectangle
            return second.getX() - first.getMaxX();
        } else {
            // First rectangle located 'under' second rectangle
            return first.getX() - second.getMaxX();
        }
    }


    static double getDistance(double i1, double i2) {
        return Math.abs(i2 - i1);
    }


    /**
     * Calculates the ration of the horizontal intersection of two lists of rectangles.<br>
     * <ul>
     * <li>If two lists differs in size, then IoU ration is 0;</li>
     * <li>Otherwise, average IoU ratio of corresponding rectangle pairs is returned;</li>
     * </ul>
     * <br>
     * For more information, see {@link RectangleUtils#getIntersectionOverUnionRatioHorizontal(ai.eldoc.model.reco.Rectangle, ai.eldoc.model.reco.Rectangle) }
     *
     * @param first
     * @param second
     * @return
     */
    public static double getIntersectionOverUnionRatioHorizontalList(List<Rectangle> first, List<Rectangle> second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty() || first.size() != second.size()) {
            return 0;
        }
        DoubleSummaryStatistics stats = new DoubleSummaryStatistics();
        for (int i = 0; i < first.size(); i++) {
            stats.accept(getIntersectionOverUnionRatioHorizontal(first.get(i), second.get(i)));
        }
        return stats.getAverage();
    }


    /**
     * Returns the ratio of horizontal intersection of two rectangles, calculated as the ratio between rectangles intersection
     * and union. This ratio is also known as Jaccard index and usually used to calculate the similarity of the objects.<br>
     * For example:<br>
     * Rectangle 1: x = 100, w = 100<br>
     * Rectangle 2: x = 50, w = 100<br>
     * Intersection: x = 100, w = 50<br>
     * Union: x = 50, w = 150<br>
     * <br>
     * IoU (Intersection over Union), or Jaccard index: 50 / 150 = 0.33 (33%)<br>
     * This metric allows to easily check, how much 'expected' and 'actual' objects matches without the need to check intersection from both sides
     *
     * @param first
     * @param second
     * @return
     */
    public static double getIntersectionOverUnionRatioHorizontal(Rectangle first, Rectangle second) {
        Range<Double> firstXRange = getRangeX(first);
        Range<Double> secondXRange = getRangeX(second);

        if (!firstXRange.isOverlappedBy(secondXRange)) {
            // Rectangles do not overlap in horizontal plane
            return 0;
        }

        Range<Double> intersection = firstXRange.intersectionWith(secondXRange);
        Range<Double> union = Range.of(Math.min(firstXRange.getMinimum(), secondXRange.getMinimum()), Math.max(firstXRange.getMaximum(), secondXRange.getMaximum()));

        double intersectionLength = intersection.getMaximum() - intersection.getMinimum();
        double unionLength = union.getMaximum() - union.getMinimum();

        return intersectionLength / unionLength;
    }


    /**
     * Calculates area intersection ratio of two {@link Rectangle}s as maximum of first rectangle area, covered by second rectangle
     * and second rectangle area, covered by first rectangle
     *
     * @param first
     * @param second
     * @return
     */
    public static double getIntersectionRatioMax(Rectangle first, Rectangle second){
        return Math.max(getIntersectionRatio(first, second), getIntersectionRatio(second, first));
    }


    /**
     * Calculates area intersection ratio of two {@link Rectangle}s
     *
     * @param first
     * @param second
     * @return Percentage of the first rectangle, that is covered by second rectangle
     */
    public static double getIntersectionRatio(Rectangle first, Rectangle second) {
        if (!first.intersects(second)) {
            return 0;
        }
        Rectangle intersection = first.createIntersection(second);
        return intersection.getArea() / first.getArea();
    }


    /**
     * Builds {@link Range} from X coordinates of provided {@link Rectangle}
     *
     * @param rect {@link Rectangle} source
     * @return
     */
    public static Range<Double> getRangeX(Rectangle rect) {
        return getRangeX(rect, true, true);
    }


    /**
     * Builds {@link Range} from X coordinates of provided {@link Rectangle}. <br>
     * <b>IMPORTANT:</b> Technically, exclusion is performed, as if Range is integer-based, so:<br>
     * [0;100] includes 0.5;<br>
     * (0;100] does not include 0.5;<br>
     *
     * @param rect {@link Rectangle} source
     * @param inclusiveStart If false, min X point will be excluded
     * @param inclusiveEnd If false, max X point will be excluded
     * @return
     */
    public static Range<Double> getRangeX(Rectangle rect, boolean inclusiveStart, boolean inclusiveEnd) {
        return Range.of(rect.getX() + (inclusiveStart ? 0 : 1), rect.getMaxX() - (inclusiveEnd ? 0 : 1));
    }


    /**
     * Builds {@link Range} from Y coordinates of provided {@link Rectangle}
     *
     * @param rect {@link Rectangle} source
     * @return
     */
    public static Range<Double> getRangeY(Rectangle rect) {
        return getRangeY(rect, true, true);
    }


    /**
     * Builds {@link Range} from Y coordinates of provided {@link Rectangle}. <br>
     * <b>IMPORTANT:</b> Technically, exclusion is performed, as if Range is integer-based, so:<br>
     * [0;100] includes 0.5;<br>
     * (0;100] does not include 0.5;<br>
     *
     * @param rect {@link Rectangle} source
     * @param inclusiveStart If false, min Y point will be excluded
     * @param inclusiveEnd If false, max Y point will be excluded
     * @return
     */
    public static Range<Double> getRangeY(Rectangle rect, boolean inclusiveStart, boolean inclusiveEnd) {
        return Range.of(rect.getY() + (inclusiveStart ? 0 : 1), rect.getMaxY() - (inclusiveEnd ? 0 : 1));
    }


    /**
     * Returns rectangle, rotated to provided angle (90, 180, 270)
     *
     * @param rect Source rectangle
     * @param boundingRect Bounding area (for example,
     * @param angle
     * @return
     */
    public static Rectangle getRotated(Rectangle rect, Rectangle boundingRect, int angle) {
        return switch (angle) {
            case 90 ->
                new Rectangle(rect.getY(), boundingRect.getWidth() - rect.getMaxX(), rect.getHeight(), rect.getWidth());
            case 270 ->
                new Rectangle(boundingRect.getHeight() - rect.getMaxY(), rect.getX(), rect.getHeight(), rect.getWidth());
            case 180 ->
                new Rectangle(boundingRect.getWidth() - rect.getMaxX(), boundingRect.getHeight() - rect.getMaxY(), rect.getWidth(), rect.getHeight());
            default ->
                rect;
        };
    }


    /**
     * Creates Rectangle from provided points (corners), taking into account it's orientation.
     * This method assumes, that points are normalized and can be from 0 to 1, where 0 corresponds to 0 coordinate, and
     * 1 corresponds to areaRect max value (for example, normalized point (0.5; 0.5) with areaRect (0; 0; 100; 200) corresponds
     * to a point with coordinates (50; 100)
     *
     * @param p0
     * @param p1
     * @param p2
     * @param p3
     * @param areaRect
     * @return
     */
    public static Rectangle fromPointsNormalized(Point2D p0, Point2D p1, Point2D p2, Point2D p3, Rectangle areaRect) {
        Rectangle normalizedRect = fromPoints(p0, p1, p2, p3);
        return new Rectangle(
                normalizedRect.getX() * areaRect.getWidth(),
                normalizedRect.getY() * areaRect.getHeight(),
                normalizedRect.getWidth() * areaRect.getWidth(),
                normalizedRect.getHeight() * areaRect.getHeight());
    }


    /**
     * Creates Rectangle from provided points (corners), taking into account it's orientation
     *
     * @param p0
     * @param p1
     * @param p2
     * @param p3
     * @return
     */
    public static Rectangle fromPoints(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        // When the text is horizontal vertices might look like:
        //     0----1
        //     |    |
        //     3----2
        //
        // when it's rotated 90 degrees around the top-left corner it becomes:
        //     3----0
        //     |    |
        //     2----1
        //
        // when it's rotated 180 degrees around the top-left corner it becomes:
        //     2----3
        //     |    |
        //     1----0
        //
        // when it's rotated 270 degrees around the top-left corner it becomes:
        //     1----2
        //     |    |
        //     0----3
        //
        // and the vertex order will still be (0, 1, 2, 3).
        //

        int orientation = getOrientation(p0, p1, p2, p3);

        double x, y, w, h;

        switch (orientation) {

            case 90:
                //      3 -------- 0
                //      |          |
                //      2 -------- 1
                x = p3.getX();
                y = p3.getY();
                w = p1.getX() - p3.getX();
                h = p1.getY() - p3.getY();
                break;

            case 180:
                //      2 -------- 3
                //      |          |
                //      1 -------- 0
                x = p2.getX();
                y = p2.getY();
                w = p0.getX() - p2.getX();
                h = p0.getY() - p2.getY();
                break;

            case 270:
                //      1 -------- 2
                //      |          |
                //      0 -------- 3
                x = p1.getX();
                y = p1.getY();
                w = p3.getX() - p1.getX();
                h = p3.getY() - p1.getY();
                break;

            default:
            case 0:
                //      0 -------- 1
                //      |          |
                //      3 -------- 2
                x = p0.getX();
                y = p0.getY();
                w = p2.getX() - p0.getX();
                h = p2.getY() - p0.getY();
                break;
        }
        return new Rectangle(x, y, w, h);
    }


    /**
     * Returns orientation based on the bounding box
     *
     * @param p0
     * @param p1
     * @param p2
     * @param p3
     * @return 0, 90, 180, 270 degree clockwise orientation
     */
    public static int getOrientation(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        List<Point2D> vertexList = List.of(p0, p1, p2, p3);
        // Calculate the center
        float centerX = 0, centerY = 0;
        for (int i = 0; i < 4; i++) {
            centerX += vertexList.get(i).getX();
            centerY += vertexList.get(i).getY();
        }
        centerX /= 4;
        centerY /= 4;

        double x0 = vertexList.get(0).getX();
        double y0 = vertexList.get(0).getY();

        if (x0 < centerX) {
            if (y0 < centerY) {
                //       0 -------- 1
                //       |          |
                //       3 -------- 2
                return 0;
            } else {
                //       1 -------- 2
                //       |          |
                //       0 -------- 3
                return 270;
            }
        } else {
            if (y0 < centerY) {
                //       3 -------- 0
                //       |          |
                //       2 -------- 1
                return 90;
            } else {
                //       2 -------- 3
                //       |          |
                //       1 -------- 0
                return 180;
            }
        }
    }
}
