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
import java.io.Serializable;
import java.util.Objects;

/**
 * Reaction model
 */
public class Reaction implements BasicMetaAwareModel, BasicAuthorAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = 6561665577420994335L;

    public static final String _TO = "to";
    public static final String _MESSAGE = "msg";
    public static final String _REF_ITEM = "ref";


    private String _id;
    private String _author;
    private String _to;
    private String _message;
    private String _ref;
    private Meta _meta;


    //
    // Constructors -------------------------------------------------------------------------------
    //


    /**
     * Constructor for Mongo Java Driver POJO Support
     */
    protected Reaction() {
        this(null);
    }


    /**
     * Creates a new model with the given arguments and initializes basic fields
     *
     * @param authorSystemId author systemId
     */
    public Reaction(String authorSystemId) {
        _author = authorSystemId;
        _meta = new Meta();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    public String getId() {
        return _id;
    }


    public void setId(String id) {
        _id = id;
    }


    @Override
    public String getAuthor() {
        return _author;
    }


    public void setAuthor(String author) {
        _author = author;
    }


    @BsonProperty(_TO)
    @JsonbProperty(_TO)
    public String getTo() {
        return _to;
    }


    @BsonProperty(_TO)
    @JsonbProperty(_TO)
    public void setTo(String to) {
        _to = to;
    }


    @BsonProperty(_MESSAGE)
    @JsonbProperty(_MESSAGE)
    public String getMessage() {
        return _message;
    }


    @BsonProperty(_MESSAGE)
    @JsonbProperty(_MESSAGE)
    public void setMessage(String message) {
        _message = message;
    }


    @BsonProperty(_REF_ITEM)
    @JsonbProperty(_REF_ITEM)
    public String getRef() {
        return _ref;
    }


    @BsonProperty(_REF_ITEM)
    @JsonbProperty(_REF_ITEM)
    public void setRef(String ref) {
        _ref = ref;
    }


    @Override
    @JsonbProperty(_META)
    @BsonProperty(_META)
    public Meta getMeta() {
        return _meta;
    }


    @JsonbProperty(_META)
    @BsonProperty(_META)
    public void setMeta(Meta meta) {
        _meta = meta;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        return Objects.hash(_id, _author, _to, _message, _ref, _meta);
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Reaction reaction = (Reaction) o;
        return Objects.equals(_id, reaction._id)
                && Objects.equals(_author, reaction._author)
                && Objects.equals(_to, reaction._to)
                && Objects.equals(_message, reaction._message)
                && Objects.equals(_ref, reaction._ref)
                && Objects.equals(_meta, reaction._meta);
    }
}
