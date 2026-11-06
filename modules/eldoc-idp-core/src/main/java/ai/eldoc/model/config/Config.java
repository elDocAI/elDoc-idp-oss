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
package ai.eldoc.model.config;

import ai.eldoc.model.BasicMetaAwareModel;
import ai.eldoc.model.Meta;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serializable;
import java.util.Objects;


/**
 * Model for holding general configuration information
 */
@BsonDiscriminator
public abstract class Config implements BasicMetaAwareModel, Serializable {

    private static final long serialVersionUID = -5050645472059236298L;

    public static final String _DISCRIMINATOR = "_t";
    public static final String _KEY = "key";

    private String _id;
    private String _key;

    private Meta _meta;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public Config() {
        _meta = new Meta();
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


    @BsonProperty(_KEY)
    public String getKey() {
        return _key;
    }


    @BsonProperty(_KEY)
    public void setKey(String key) {
        _key = key;
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


    @Override
    public int hashCode() {
        int hash = 5;
        hash = 47 * hash + Objects.hashCode(_id);
        hash = 47 * hash + Objects.hashCode(_key);
        hash = 47 * hash + Objects.hashCode(_meta);
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
        final Config other = (Config) obj;
        if (!Objects.equals(_id, other._id)) {
            return false;
        }
        if (!Objects.equals(_key, other._key)) {
            return false;
        }
        if (!Objects.equals(_meta, other._meta)) {
            return false;
        }
        return true;
    }

}
