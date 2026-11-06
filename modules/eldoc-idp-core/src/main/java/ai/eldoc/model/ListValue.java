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
import ai.eldoc.api.RestApiVisibilityStrategy;
import ai.eldoc.constants.DirectoryConstants;
import jakarta.json.bind.annotation.*;
import org.apache.commons.lang3.ArrayUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;


/**
 * List Value Model
 */
@JsonbVisibility(RestApiVisibilityStrategy.class) // when private property and public getter with the same name require special handling
public class ListValue implements BasicAclAwareModel, BasicMetaAwareModel, BasicAuthorAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = 8457697458827642186L;

    public static final String _ID_EXT = BasicModel._ID_EXT;
    public static final String _ID_KF = BasicModel._ID_KF;
    public static final String _STATUS = "status";
    public static final String _VALUE = "value";
    public static final String _TYPE = "type";

    public static final int TYPE_DOC_RESOLUTION = 1;
    public static final int TYPE_FORM_CATEGORY = 4;

    /**
     * Array with all system types of ListValues
     */
    public static final int[] TYPE_ALL = {TYPE_DOC_RESOLUTION, TYPE_FORM_CATEGORY};

    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_DELETED = 10;

    private String _id;
    private String _idExt;
    private String _idKf;
    private int _status;
    private String _author;
    private Map<String, String> _value;
    private int _type;
    private Meta _meta;
    private Acl _acl;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    /**
     * No-argument constructor to be used for deserialization mostly
     */
    protected ListValue() {
        this(null, null, null);
    }


    /**
     * ListValue Constructor
     *
     * @param authorSystemId
     * @param writeAccess
     * @param readAccess
     */
    public ListValue(String authorSystemId, Set<String> writeAccess, Set<String> readAccess) {
        _author = authorSystemId;
        _value = new HashMap<>();
        _meta = new Meta();
        _acl = new Acl();

        // Initialization of ACL
        if (writeAccess != null) {
            _acl.getW().addAll(writeAccess);
        }

        if (readAccess != null) {
            _acl.getR().addAll(readAccess);
        }
    }


    /**
     * Creates new ListValue with default ACL.W access for [elAdminDocsRW]
     *
     * @param systemId
     * @return
     */
    public static ListValue createForDocs(String systemId) {
        return new ListValue(systemId, Set.of(DirectoryConstants.ROLE_ADMINDOCRW), Set.of(DirectoryConstants.ROLE_ALLUSERS));
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns true in case current record is a system one
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public boolean isSystem() {
        return ArrayUtils.contains(TYPE_ALL, getType());
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isDeleted() {
        return STATUS_DELETED == getStatus();
    }


    //
    // Getters and setters ------------------------------------------------------------------------
    //
    @Override
    @RestApiVisible
    @JsonbProperty(_ID)
    public String getId() {
        return _id;
    }


    @RestApiVisible
    @JsonbProperty(_ID)
    public void setId(String id) {
        _id = id;
    }


    @RestApiVisible
    @BsonProperty(_ID_EXT)
    @JsonbProperty(_ID_EXT)
    public String getIdExt() {
        return _idExt;
    }


    @RestApiVisible(writable = true)
    @BsonProperty(_ID_EXT)
    @JsonbProperty(_ID_EXT)
    public void setIdExt(String idExt) {
        _idExt = idExt;
    }


    /**
     * Return reference to the parent ListValue
     *
     * @return
     */
    @RestApiVisible
    @BsonProperty(_ID_KF)
    @JsonbProperty(_ID_KF)
    public String getIdKf() {
        return _idKf;
    }


    /**
     * Sets reference to the parent ListValue
     *
     * @param idKf
     */
    @RestApiVisible(writable = true)
    @BsonProperty(_ID_KF)
    @JsonbProperty(_ID_KF)
    public void setIdKf(String idKf) {
        _idKf = idKf;
    }


    @Override
    @RestApiVisible
    @JsonbProperty(_AUTHOR)
    public String getAuthor() {
        return _author;
    }


    @RestApiVisible(writable = true)
    @JsonbProperty(_AUTHOR)
    public void setAuthor(String author) {
        _author = author;
    }


    @RestApiVisible
    @JsonbProperty(_STATUS)
    public int getStatus() {
        return _status;
    }


    @RestApiVisible(writable = true)
    @JsonbProperty(_STATUS)
    public void setStatus(int status) {
        _status = status;
    }


    /**
     * Returns map with per-language values
     *
     * @return
     */
    @RestApiVisible
    @JsonbProperty(_VALUE)
    public Map<String, String> getValue() {
        return _value;
    }


    /**
     * Sets map with per-language values
     *
     * @param data
     */
    @RestApiVisible(writable = true)
    @JsonbProperty(_VALUE)
    public void setValue(Map<String, String> data) {
        _value.clear();
        if (data != null) {
            _value.putAll(data);
        }
    }


    /**
     * Sets value for the specific language
     *
     * @param lang lang code used in the system
     * @param name CustomField label (name)
     */
    public void setValue(String lang, String name) {
        _value.put(lang, name);
    }


    /**
     * Returns value for the specific language or empty string<br>
     * IMPORTANT: in case no value is present for the given language then empty string is returned instead of null!
     * This also helps to resolve NPE in code like lvList.sort(Comparator.comparing(listValue -> listValue.getValue(lang)))
     *
     * @param lang
     * @return
     */
    public String getValue(String lang) {
        String valueForLang = _value.get(lang);
        return valueForLang == null ? "" : valueForLang;
    }


    /**
     * Returns type of this ListValue (kind of a system type)
     *
     * @return
     */
    @RestApiVisible
    @JsonbProperty(_TYPE)
    public int getType() {
        return _type;
    }


    /**
     * Sets type of this ListValue (kind of a system type)
     *
     * @param type
     */
    @JsonbProperty(_TYPE)
    public void setType(int type) {
        _type = type;
    }


    @Override
    @BsonProperty(_ACL)
    public Acl getAcl() {
        return _acl;
    }


    @BsonProperty(_ACL)
    public void setAcl(Acl acl) {
        _acl = acl;
    }


    @Override
    @RestApiVisible
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


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        return "ListValue{" + "_id=" + _id + ", _value=" + _value + '}';
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ListValue listValue = (ListValue) o;
        return _status == listValue._status
                && _type == listValue._type
                && Objects.equals(_id, listValue._id)
                && Objects.equals(_idExt, listValue._idExt)
                && Objects.equals(_idKf, listValue._idKf)
                && Objects.equals(_author, listValue._author)
                && Objects.equals(_value, listValue._value)
                && Objects.equals(_meta, listValue._meta)
                && Objects.equals(_acl, listValue._acl);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_id, _idExt, _idKf, _status, _author, _value, _type, _meta, _acl);
    }
}
