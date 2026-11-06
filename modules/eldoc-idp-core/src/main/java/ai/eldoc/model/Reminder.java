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

import org.apache.commons.collections4.CollectionUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * Reminder Model
 */
public class Reminder implements BasicMetaAwareModel, BasicAuthorAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = 2380441125093651763L;

    public static final String _STATUS = "stts";
    public static final String _NOTE = "note";
    public static final String _REF_ID = "ref";
    public static final String _REF_TYPE = "refTyp";
    public static final String _REMINDER_AT = "at";
    public static final String _RECIPIENTS = "rcpt";

    public final static int STATUS_DRAFT = 0; // Reminder is in draft state
    public final static int STATUS_ACTIVE = 1; // Reminder is active
    public final static int STATUS_DISABLED = 10; // Reminder is disabled

    private String _id;
    private String _author;
    private int _status;
    private String _note;
    private String _refId;
    private String _refType;
    private final List<String> _recipients;
    private Date _at; // Reminder date
    private Meta _meta;


    //
    // Cunstructors -------------------------------------------------------------------------------
    //


    /**
     * Constructor for Mongo Java Driver POJO Support
     */
    protected Reminder() {
        this(null);
    }


    /**
     * Creates a new model with the given arguments and initializes basic fields
     *
     * @param authorSystemId author systemId
     */
    public Reminder(String authorSystemId) {
        _author = authorSystemId;
        _meta = new Meta();
        _recipients = new ArrayList<>();
        setStatus(STATUS_ACTIVE);
        setAt(new Date());
    }

    //
    // Methods ------------------------------------------------------------------------------------
    //


    @BsonIgnore
    public boolean isOverdue() {
        return getAt() != null && getAt().toInstant().isBefore(Instant.now());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    @Override
    @BsonProperty(_ID)
    public String getId() {
        return _id;
    }


    @BsonProperty(_ID)
    public void setId(String id) {
        _id = id;
    }


    @Override
    @BsonProperty(_AUTHOR)
    public String getAuthor() {
        return _author;
    }


    @BsonProperty(_AUTHOR)
    public void setAuthor(String author) {
        _author = author;
    }


    @BsonProperty(_STATUS)
    public int getStatus() {
        return _status;
    }


    @BsonProperty(_STATUS)
    public void setStatus(int status) {
        _status = status;
    }


    @BsonProperty(_NOTE)
    public String getNote() {
        return _note;
    }


    @BsonProperty(_NOTE)
    public void setNote(String note) {
        _note = note;
    }


    @BsonProperty(_REF_ID)
    public String getRefId() {
        return _refId;
    }


    @BsonProperty(_REF_ID)
    public void setRefId(String refId) {
        _refId = refId;
    }


    @BsonProperty(_REF_TYPE)
    public String getRefType() {
        return _refType;
    }


    @BsonProperty(_REF_TYPE)
    public void setRefType(String refType) {
        _refType = refType;
    }


    @BsonProperty(_RECIPIENTS)
    public List<String> getRecipients() {
        return _recipients;
    }


    @BsonProperty(_RECIPIENTS)
    public void setRecipients(List<String> recipients) {
        _recipients.clear();
        _recipients.addAll(CollectionUtils.emptyIfNull(recipients).stream().distinct().toList());
    }


    @BsonProperty(_REMINDER_AT)
    public Date getAt() {
        return _at;
    }


    @BsonProperty(_REMINDER_AT)
    public void setAt(Date at) {
        _at = at;
    }


    @Override
    @BsonProperty(_META)
    public Meta getMeta() {
        return _meta;
    }


    @BsonProperty(_META)
    public void setMeta(Meta meta) {
        _meta = meta;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public String toString() {
        return "Reminder{" +
                "_id='" + _id + '\'' +
                ", _author='" + _author + '\'' +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Reminder reminder = (Reminder) o;
        return _status == reminder._status
                && Objects.equals(_id, reminder._id)
                && Objects.equals(_author, reminder._author)
                && Objects.equals(_note, reminder._note)
                && Objects.equals(_refId, reminder._refId)
                && Objects.equals(_refType, reminder._refType)
                && Objects.equals(_recipients, reminder._recipients)
                && Objects.equals(_at, reminder._at)
                && Objects.equals(_meta, reminder._meta);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_id, _author, _status, _note, _refId, _refType, _recipients, _at, _meta);
    }
}
