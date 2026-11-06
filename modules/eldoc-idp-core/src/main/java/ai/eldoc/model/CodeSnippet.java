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

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import java.util.Objects;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * CodeSnippet model. Used to store external code.
 */
public class CodeSnippet implements BasicMetaAwareModel {

    @Serial
    private static final long serialVersionUID = 2879574550233177130L;

    public static final String _TYPE = "typ";
    public static final String _CODE = "code";

    public static final int TYPE_RECO = 1;

    private String _id;
    private int _type;
    private String _code;

    private Meta _meta;


    //
    // Constructor --------------------------------------------------------------------------------
    //


    /**
     * CodeSnippet Model main constructor
     */
    public CodeSnippet() {
        _type = TYPE_RECO;
        _meta = new Meta();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_ID)
    @Override
    public String getId() {
        return _id;
    }


    @BsonProperty(_ID)
    public void setId(String id) {
        _id = id;
    }


    @BsonProperty(_TYPE)
    public int getType() {
        return _type;
    }


    @BsonProperty(_TYPE)
    public void setType(int type) {
        _type = type;
    }


    @BsonProperty(_CODE)
    public String getCode() {
        return _code;
    }


    @BsonProperty(_CODE)
    public void setCode(String code) {
        _code = code;
    }


    @BsonIgnore
    public String getCodeDecoded() throws ParseException {
        if (isBlank(getCode())) {
            return "";
        }
        return new String(Base64.getDecoder().decode(getCode()), StandardCharsets.UTF_8);
    }


    @BsonProperty(_META)
    @Override
    public Meta getMeta() {
        return _meta;
    }


    @BsonProperty(_META)
    public void setMeta(Meta meta) {
        _meta = meta;
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CodeSnippet that = (CodeSnippet) o;
        return _type == that._type
                && Objects.equals(_id, that._id)
                && Objects.equals(_code, that._code)
                && Objects.equals(_meta, that._meta);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_id, _type, _code, _meta);
    }
}
