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
package ai.eldoc.model;

import jakarta.json.*;
import java.io.Serializable;
import java.io.StringReader;
import java.util.*;


/**
 * Stores PrimeFaces-related representation of {@link CustomField} of type Signature
 */
public class CustomFieldSign implements Serializable {

    private static final long serialVersionUID = 1541147364258888836L;

    private List<List<Point>> _lines;


    //
    // Constructors
    //
    public CustomFieldSign() {
        _lines = new ArrayList<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Creates new {@link CustomFieldSign} by parsing provided JSON string.. <br/>
     * <b>IMPORTANT:</b> In PrimeFaces 8, p:signature stores Point coords as array of floats, and not object with (x, y) coordinates,
     * therefore custom parsing is required. Adjust this method, if this behaviour is changed
     *
     * @param jsonStr
     * @return
     */
    public static CustomFieldSign fromJson(String jsonStr) {
        CustomFieldSign result = new CustomFieldSign();
        try (JsonReader reader = Json.createReader(new StringReader(jsonStr))) {
            JsonObject object = reader.readObject();
            object.getJsonArray("lines").forEach(jsonLine -> {
                List<Point> line = new ArrayList<>();
                result.getLines().add(line);
                jsonLine.asJsonArray().forEach(jsonPointArray -> {
                    JsonArray pointArray = jsonPointArray.asJsonArray();
                    Point point = new Point(Float.parseFloat(pointArray.getJsonNumber(0).toString()), Float.parseFloat(pointArray.getJsonNumber(1).toString()));
                    line.add(point);
                });
            });
        }

        return result;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public List<List<Point>> getLines() {
        return _lines;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public void setLines(List<List<Point>> lines) {
        _lines = lines;
    }


    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(_lines);
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
        final CustomFieldSign other = (CustomFieldSign) obj;
        if (!Objects.equals(_lines, other._lines)) {
            return false;
        }
        return true;
    }


    /**
     * A point along a line within a signature <br/>
     * <b>IMPORTANT:</b> Rounds coordinates to Integer values
     */
    public static class Point implements Serializable {

        private static final long serialVersionUID = 5075147186686814985L;

        private final int _x;
        private final int _y;


        /**
         * <b>IMPORTANT:</b> Rounds coordinates to Integer values
         *
         * @param x
         */
        public Point(float x, float y) {
            _x = Math.round(x);
            _y = Math.round(y);
        }


        public int getX() {
            return _x;
        }


        public int getY() {
            return _y;
        }


        @Override
        public int hashCode() {
            int hash = 3;
            hash = 41 * hash + this._x;
            hash = 41 * hash + this._y;
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
            final Point other = (Point) obj;
            if (this._x != other._x) {
                return false;
            }
            if (this._y != other._y) {
                return false;
            }
            return true;
        }

    }

}
