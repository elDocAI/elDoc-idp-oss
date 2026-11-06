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

package ai.eldoc.bpm.v3;

import ai.eldoc.bpm.ProcessSubject;
import com.google.gson.annotations.SerializedName;
import jakarta.json.bind.annotation.JsonbProperty;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;


/**
 * BPM Process Subject Model (Implementation v3)
 */
public class ProcessSubjectImpl implements ProcessSubject, Serializable {

    @Serial
    private static final long serialVersionUID = -1632149092793621324L;

    public static final String _ID = "id";
    public static final String _NAME = "nm";

    @SerializedName(_ID)
    private String _id;

    /**
     * Stores name of the external user added ad hoc to the process
     *
     * @since 5.10.0
     */
    @SerializedName(_NAME)
    private String _nm;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public ProcessSubjectImpl() {
    }


    public ProcessSubjectImpl(String systemId) {
        this();
        _id = systemId;
    }


    public ProcessSubjectImpl(String email, String name) {
        this();
        _id = email;
        _nm = name;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    public void setId(String systemId) {
        _id = systemId;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return _id;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_NAME)
    @JsonbProperty(_NAME)
    public String getName() {
        return _nm;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_NAME)
    @JsonbProperty(_NAME)
    public void setName(String name) {
        _nm = name;
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + Objects.hashCode(_id);
        hash = 19 * hash + Objects.hashCode(_nm);
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
        final ProcessSubjectImpl other = (ProcessSubjectImpl) obj;
        if (!Objects.equals(_id, other._id)) {
            return false;
        }
        return Objects.equals(_nm, other._nm);
    }

}
