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
 * Standard Fields Definition Model
 */
public class StandardFieldDef implements Serializable {

    @Serial
    private static final long serialVersionUID = 6382082867408982396L;

    public static final String _NUMBER = "num";
    public static final String _TOPIC = "topic";
    public static final String _DESCRIPTION = "descr";
    public static final String _DATE_CREATED = "dtCreated";
    public static final String _DATE_DUE = "dtDue";


    private StandardFieldNum _num = new StandardFieldNum();
    private StandardFieldTopic _topic = new StandardFieldTopic();
    private StandardFieldDescription _descr = new StandardFieldDescription();
    private StandardFieldDateCreated _dtCreated = new StandardFieldDateCreated();
    private StandardFieldDateDue _dtDue = new StandardFieldDateDue();


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    @BsonProperty(_NUMBER)
    @JsonbProperty(_NUMBER)
    public void setNum(StandardFieldNum docNum) {
        _num = docNum;
    }


    @BsonProperty(_NUMBER)
    @JsonbProperty(_NUMBER)
    public StandardFieldNum getNum() {
        return _num;
    }


    @BsonProperty(_TOPIC)
    @JsonbProperty(_TOPIC)
    public void setTopic(StandardFieldTopic docTopic) {
        _topic = docTopic;
    }


    @BsonProperty(_TOPIC)
    @JsonbProperty(_TOPIC)
    public StandardFieldTopic getTopic() {
        return _topic;
    }


    @BsonProperty(_DESCRIPTION)
    @JsonbProperty(_DESCRIPTION)
    public void setDescription(StandardFieldDescription description) {
        _descr = description;
    }


    @BsonProperty(_DESCRIPTION)
    @JsonbProperty(_DESCRIPTION)
    public StandardFieldDescription getDescription() {
        return _descr;
    }


    @BsonProperty(_DATE_CREATED)
    @JsonbProperty(_DATE_CREATED)
    public StandardFieldDateCreated getDateCreated() {
        return _dtCreated;
    }


    @BsonProperty(_DATE_CREATED)
    @JsonbProperty(_DATE_CREATED)
    public void setDateCreated(StandardFieldDateCreated dateCreated) {
        _dtCreated = dateCreated;
    }


    @BsonProperty(_DATE_DUE)
    @JsonbProperty(_DATE_DUE)
    public StandardFieldDateDue getDateDue() {
        return _dtDue;
    }


    @BsonProperty(_DATE_DUE)
    @JsonbProperty(_DATE_DUE)
    public void setDateDue(StandardFieldDateDue dtDue) {
        _dtDue = dtDue;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        StandardFieldDef that = (StandardFieldDef) o;
        return Objects.equals(_num, that._num)
                && Objects.equals(_topic, that._topic)
                && Objects.equals(_descr, that._descr)
                && Objects.equals(_dtCreated, that._dtCreated)
                && Objects.equals(_dtDue, that._dtDue);
    }


    @Override
    public int hashCode() {
        return Objects.hash(
                _num,
                _topic,
                _descr,
                _dtCreated,
                _dtDue
        );
    }

}
