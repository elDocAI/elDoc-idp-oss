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

import ai.eldoc.annotation.RestApiVisible;
import ai.eldoc.api.RestApiVisibilityStrategy;
import ai.eldoc.util.JsonbUtils;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbVisibility;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import static ai.eldoc.model.reco.RecoForm._FORM;
import static ai.eldoc.model.reco.RecoForm._NAME;


/**
 * RecoForm DTO
 */
@JsonbVisibility(RestApiVisibilityStrategy.class) // when private property and public getter with the same name require special handling
public class RecoFormDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 5024882362399014755L;

    private static final String _ID = "id";// This is for Jsonb toJson serialization. We use id instead of _id because id contains String value, whereas _id contains ObjectId

    private String _id;
    private String _name;
    private String _form;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * RecoFormDto Model internal constructor, mostly used for Jsonb deserialization
     */
    protected RecoFormDto() {
    }


    public RecoFormDto(RecoForm recoForm) {
        _id = recoForm.getId();
        _name = recoForm.getName();
        _form = recoForm.getForm();
    }


    public RecoFormDto(Document recoFormDoc) {
        _id = recoFormDoc.getString(_ID);
        _name = recoFormDoc.getString(_NAME);
        _form = recoFormDoc.getString(_FORM);
    }


    @BsonCreator
    public RecoFormDto(@BsonProperty(_ID) String id, @BsonProperty(_NAME) String name, @BsonProperty(_FORM) String form) {
        _id = id;
        _name = name;
        _form = form;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public String toJson() {
        return JsonbUtils.getInstance().toJson(this);
    }


    public static RecoFormDto fromJson(String recoFormJson) {
        return JsonbUtils.getInstance().fromJson(recoFormJson, RecoFormDto.class);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @RestApiVisible
    @JsonbProperty(_ID)
    public String getId() {
        return _id;
    }


    @JsonbProperty(_ID)
    public void setId(String id) {
        _id = id;
    }


    @RestApiVisible
    @JsonbProperty(_NAME)
    public String getName() {
        return _name;
    }


    @JsonbProperty(_NAME)
    public void setName(String name) {
        _name = name;
    }


    @RestApiVisible
    @JsonbProperty(_FORM)
    public String getForm() {
        return _form;
    }


    @JsonbProperty(_FORM)
    public void setForm(String form) {
        _form = form;
    }


    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(_id);
        hash = 89 * hash + Objects.hashCode(_name);
        hash = 89 * hash + Objects.hashCode(_form);
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
        final RecoFormDto other = (RecoFormDto) obj;
        if (!Objects.equals(_id, other._id)) {
            return false;
        }
        if (!Objects.equals(_name, other._name)) {
            return false;
        }
        if (!Objects.equals(_form, other._form)) {
            return false;
        }
        return true;
    }

}
