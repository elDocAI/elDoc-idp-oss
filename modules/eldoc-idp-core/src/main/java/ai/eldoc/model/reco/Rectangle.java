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
package ai.eldoc.model.reco;

import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.apache.commons.lang3.SerializationUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Model represent Rectangle data
 */
public class Rectangle extends Rectangle2D.Double implements Serializable {

    private static final long serialVersionUID = 396874866281077498L;

    public static final String _X = "x";
    public static final String _Y = "y";
    public static final String _WIDTH = "w";
    public static final String _HEIGHT = "h";

    private static final Pattern RECT_STR_PATTERN = Pattern.compile("\\((-?\\d+(?:\\.\\d+)?);(-?\\d+(?:\\.\\d+)?);(\\d+(?:\\.\\d+)?);(\\d+(?:\\.\\d+)?)\\)");
    public static final String RECT_DEFAULT_STR = "(300;0;300;150)";


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * Default constructor with all values set to 0
     */
    public Rectangle() {
        super();
    }


    /**
     * Constructor with possibility to define parameters
     *
     * @param x
     * @param y
     * @param w
     * @param h
     */
    public Rectangle(double x, double y, double w, double h) {
        super(x, y, w, h);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Creates a deep clone using Serialization-based cloning approach
     *
     * @return
     */
    @Override
    public Rectangle clone() {
        return SerializationUtils.clone(this);
    }


    public static Rectangle fromString(String value) throws IllegalArgumentException {
        Matcher matcher = RECT_STR_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Rectangle format is wrong: " + value);
        }
        Rectangle rect = new Rectangle(
                java.lang.Double.parseDouble(matcher.group(1)),
                java.lang.Double.parseDouble(matcher.group(2)),
                java.lang.Double.parseDouble(matcher.group(3)),
                java.lang.Double.parseDouble(matcher.group(4)));
        return rect;
    }


    /**
     * Returns clone of this rectangle after applying provided scale<br>
     * This method assumes scaling as scaling canvas, where rectangle is located, so returned rectangle
     * will have both different size and position
     *
     * @param scale
     * @return
     */
    public Rectangle getScaledClone(double scale) {
        return new Rectangle(getX() * scale, getY() * scale, getWidth() * scale, getHeight() * scale);
    }


    /**
     * Translates this Rectangle the indicated distance, to the right along the X coordinate axis, and downward along the Y coordinate axis.
     *
     * @param dx the distance to move this Rectangle along the X axis
     * @param dy the distance to move this Rectangle along the Y axis
     * @return
     */
    public Rectangle translate(double dx, double dy) {
        setX(getX() + dx);
        setY(getY() + dy);
        return this;
    }


    /**
     * Scales this Rectangle in all direction, keeping it's center in same point
     *
     * @param scale
     */
    public void scalePreservingCenter(double scale) {
        setX(getX() - getWidth() * (scale - 1) / 2);
        setY(getY() - getHeight() * (scale - 1) / 2);
        setWidth(getWidth() * scale);
        setHeight(getHeight() * scale);
    }


    /**
     * Scales this rectangle, keeping its ratio, as close as possible to provided rectangle<br>
     * For example, scaling square rect 20x20 to 100x200 will result in 100x100 rectangle
     *
     * @param newRect Target rectangle (used as 'max' size in both width and height)
     */
    public void scalePreservingRatio(Rectangle newRect) {
        double scale = Math.abs(Math.min(newRect.getWidth() / getWidth(), newRect.getHeight() / getHeight()));
        setWidth(getWidth() * scale);
        setHeight(getHeight() * scale);
    }


    /**
     * Returns a new Rectangle object representing the intersection of this Rectangle with the specified Rectangle
     *
     * @param rect
     * @return
     */
    public Rectangle createIntersection(Rectangle rect) {
        Rectangle dest = new Rectangle();
        Rectangle2D.intersect(this, rect, dest);
        return dest;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_X)
    @JsonbProperty(_X)
    @Override
    public final double getX() {
        return super.getX();
    }


    @BsonProperty(_X)
    @JsonbProperty(_X)
    public final void setX(double x) {
        this.x = x;
    }


    @BsonProperty(_Y)
    @JsonbProperty(_Y)
    @Override
    public final double getY() {
        return super.getY();
    }


    @BsonProperty(_Y)
    @JsonbProperty(_Y)
    public final void setY(double y) {
        this.y = y;
    }


    @BsonProperty(_WIDTH)
    @JsonbProperty(_WIDTH)
    @Override
    public final double getWidth() {
        return super.getWidth();
    }


    @BsonProperty(_WIDTH)
    @JsonbProperty(_WIDTH)
    public final void setWidth(double width) {
        this.width = width;
    }


    @BsonProperty(_HEIGHT)
    @JsonbProperty(_HEIGHT)
    @Override
    public final double getHeight() {
        return super.getHeight();
    }


    public final void setHeight(double height) {
        this.height = height;
    }


    @BsonIgnore
    @JsonbTransient
    public double getArea() {
        return getWidth() * getHeight();
    }


    /**
     * Returns String representation of this model<br>
     * All values (x, y, w, h) are rounded to 3 decimal points precision, i.e.:<br>
     * <ul>
     * <li>496.3881509385866 -> 496.388</li>
     * <li>79.00567142857142 -> 79.006</li>
     * </ul>
     *
     * @return
     */
    public String toStringShort() {
        // TODO: Validate usage of toString() in IDP-related logic and consider replacing toString() with this implementation
        // Usage in PropertyAware model to be considered, see TextExtendableProperties.getBottomAnchor - with this implementation, following code may return false, if stored rectangle 'rect' has higher precision:
        // props.setBottomAnchor(rect);
        // rect.equals(props.getBottomAnchor())
        return new StringBuilder().append("(").append(Math.round(getX() * 1000) / 1000.0).append(";").append(Math.round(getY() * 1000) / 1000.0).append(";").append(Math.round(getWidth() * 1000) / 1000.0).append(";").append(Math.round(getHeight() * 1000) / 1000.0).append(")").toString();
    }


    /**
     * Returns String representation of this model<br>
     * IMPORTANT: do not alter as it is used in the code
     *
     * @return
     */
    @Override
    public String toString() {
        // DO NOT ALTER - USED IN THE CODE
        return new StringBuilder().append("(").append(getX()).append(";").append(getY()).append(";").append(getWidth()).append(";").append(getHeight()).append(")").toString();
    }
}
