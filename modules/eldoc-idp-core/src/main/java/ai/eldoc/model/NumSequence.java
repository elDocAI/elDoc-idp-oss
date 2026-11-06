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

import jakarta.json.bind.annotation.JsonbProperty;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.util.Date;
import java.util.Objects;


/**
 * NumberSequence Model
 */
public class NumSequence implements BasicMetaAwareModel {

    @Serial
    private static final long serialVersionUID = -1937027279836459437L;

    public static final String _NAME = "nm"; // Counter name (counter referenced by this name)
    public static final String _VNUM = "vnum"; // Counter Value content Number
    public static final String _VDATE = "vdate"; // Counter Value content DateTime
    public static final String _VTEXT = "vtext"; // Counter Value content Text template

    private String _id;
    private String _name;
    private String _vText;
    private int _vNum;
    private Date _vDate;

    // ACL and Meta Interfaces related fields
    private Meta _meta;


    //
    // Cunstructors -------------------------------------------------------------------------------
    //


    /**
     * NumSequence Model main constructor
     */
    public NumSequence() {
        _vDate = new Date();
        _meta = new Meta();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    @JsonbProperty(_ID)
    @BsonProperty(_ID)
    public String getId() {
        return _id;
    }


    @JsonbProperty(_ID)
    @BsonProperty(_ID)
    public void setId(String id) {
        _id = id;
    }


    @JsonbProperty(_NAME)
    @BsonProperty(_NAME)
    public String getName() {
        return _name;
    }


    @JsonbProperty(_NAME)
    @BsonProperty(_NAME)
    public void setName(String name) {
        _name = name;
    }


    @JsonbProperty(_VTEXT)
    @BsonProperty(_VTEXT)
    public String getVText() {
        return _vText;
    }


    @JsonbProperty(_VTEXT)
    @BsonProperty(_VTEXT)
    public void setVText(String vText) {
        _vText = vText;
    }


    @JsonbProperty(_VNUM)
    @BsonProperty(_VNUM)
    public int getVNum() {
        return _vNum;
    }


    @JsonbProperty(_VNUM)
    @BsonProperty(_VNUM)
    public void setVNum(int vNum) {
        _vNum = vNum;
    }


    @JsonbProperty(_VDATE)
    @BsonProperty(_VDATE)
    public Date getVDate() {
        return _vDate;
    }


    @JsonbProperty(_VDATE)
    @BsonProperty(_VDATE)
    public void setVDate(Date vDate) {
        _vDate = vDate;
    }


    @Override
    @BsonProperty(_META)
    @JsonbProperty(_META)
    public Meta getMeta() {
        return _meta;
    }


    @BsonProperty(_META)
    @JsonbProperty(_META)
    public void setMeta(Meta meta) {
        _meta = meta;
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        NumSequence that = (NumSequence) o;
        return _vNum == that._vNum
                && Objects.equals(_id, that._id)
                && Objects.equals(_name, that._name)
                && Objects.equals(_vText, that._vText)
                && Objects.equals(_vDate, that._vDate)
                && Objects.equals(_meta, that._meta);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_id, _name, _vText, _vNum, _vDate, _meta);
    }
}
