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

import ai.eldoc.annotation.RestApiVisible;
import ai.eldoc.bpm.*;
import ai.eldoc.bpm.Process;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * BPM Process Definition Model
 */
public class BpmProcessDefinition implements BPMProcessEntity, BasicMetaAwareModel, BasicAuthorAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = -1733820924896589999L;

    public static final String RECO_PROCESS_ID = "000000000000000000000010";

    public static final String _NAME = "nm";
    public static final String _PROCESS = "prcs";

    private String _id;
    private String _name;
    private String _author;
    private Process _process;
    private Meta _meta;


    //
    // Cunstructors -------------------------------------------------------------------------------
    //


    /**
     * Constructor for Mongo Java Driver POJO Support
     */
    protected BpmProcessDefinition() {
        this(null);
    }


    /**
     * Creates new model with the given arguments and initializes basic fields
     *
     * @param authorSystemId author systemId
     */
    public BpmProcessDefinition(String authorSystemId) {
        _author = authorSystemId;
        _name = "New workflow";
        _process = ProcessUtils.createProcess();
        _meta = new Meta();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Return true in case the given process _id is a Reco Process
     *
     * @param processId database _id of the process
     * @return
     */
    public static boolean isRecoProcess(String processId) {
        return RECO_PROCESS_ID.equals(processId);
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


    @Override
    @JsonbProperty(_PROCESS)
    @BsonProperty(_PROCESS)
    public Process getProcess() {
        return _process;
    }


    @JsonbProperty(_PROCESS)
    @BsonProperty(_PROCESS)
    public void setProcess(Process process) {
        _process = process;
    }


    @Override
    @BsonIgnore
    @JsonbTransient
    public String getProcessId() {
        return getId();
    }


    @Override
    @RestApiVisible
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


    /**
     * Return true in case this is an Reco Process
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public boolean isRecoProcess() {
        return isRecoProcess(getId());
    }

    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public String toString() {
        return "BpmProcessDefinition{" +
                "_id='" + _id + "'," +
                "_name='" + _name + '\'' +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BpmProcessDefinition that = (BpmProcessDefinition) o;
        return Objects.equals(_id, that._id)
                && Objects.equals(_name, that._name)
                && Objects.equals(_author, that._author)
                && Objects.equals(_process, that._process)
                && Objects.equals(_meta, that._meta);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_id, _name, _author, _process, _meta);
    }
}
