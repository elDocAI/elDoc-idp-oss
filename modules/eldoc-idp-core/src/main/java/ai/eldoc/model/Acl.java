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
import jakarta.json.bind.annotation.JsonbTransient;
import org.apache.commons.collections4.SetUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serializable;
import java.util.*;
import static java.util.Collections.disjoint;


/**
 * Access-Control List Model
 */
public class Acl implements Serializable {

    private static final long serialVersionUID = 7143436897145854994L;

    /**
     * w : write permission
     */
    public static final String _W = "w";

    /**
     * wh: write (hierarchical permission)
     */
    public static final String _WH = "wh";

    /**
     * r : read permission
     */
    public static final String _R = "r";

    /**
     * rh: read (hierarchical permission)
     */
    public static final String _RH = "rh";

    private final Set<String> _r;  // read permission
    private final Set<String> _rh; // read (hierarchical permission)
    private final Set<String> _w;  // write permission
    private final Set<String> _wh; // write (hierarchical permission)


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public Acl() {
        _w = new HashSet<>();
        _wh = new HashSet<>();
        _r = new HashSet<>();
        _rh = new HashSet<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Sets accesses in this ACL to the ones listed in the give ACL
     *
     * @param acl
     * @return {@code true } if this ACL changed as a result of the call
     */
    public boolean set(Acl acl) {
        if (!this.equals(acl)) {
            setW(acl.getW());
            setWH(acl.getWH());
            setR(acl.getR());
            setRH(acl.getRH());
            return true;
        }
        return false;
    }


    /**
     * Adds accesses to this ACL which are listed in the given ACL
     *
     * @param acl
     * @return {@code true } if this ACL changed as a result of the call
     */
    public boolean add(Acl acl) {
        return getW().addAll(acl.getW())
                | getWH().addAll(acl.getWH())
                | getR().addAll(acl.getR())
                | getRH().addAll(acl.getRH());
    }


    /**
     * Removes accesses from this ACL which are listed in the given ACL
     *
     * @param acl
     * @return {@code true } if this ACL changed as a result of the call
     */
    public boolean remove(Acl acl) {
        return getW().removeAll(acl.getW())
                | getWH().removeAll(acl.getWH())
                | getR().removeAll(acl.getR())
                | getRH().removeAll(acl.getRH());
    }


    /**
     * Checks if this ACL is allowed to be readable by the given AclContext
     *
     * @param aclContext
     * @return
     */
    public boolean isReadable(AclContext aclContext) {
        return !disjoint(getR(), aclContext.toSet())
                || !disjoint(getW(), aclContext.toSet())
                || !disjoint(getRH(), aclContext.getHier())
                || !disjoint(getWH(), aclContext.getHier())
                || aclContext.isFullAccessMode();
    }


    /**
     * Checks if this ACL is allowed to be writable by the given AclContext
     *
     * @param aclContext
     * @return
     */
    public boolean isWritable(AclContext aclContext) {
        return !disjoint(getW(), aclContext.toSet())
                || !disjoint(getWH(), aclContext.getHier())
                || aclContext.isFullAccessMode();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns set of entities with write-access
     *
     * @return
     */
    public Set<String> getW() {
        return _w;
    }


    public void setW(Set<String> w) {
        _w.clear();
        if (w != null) {
            _w.addAll(w);
        }
    }


    /**
     * Returns set of entities with write-access (hierarchical).
     * Commonly used with OrgUnit systemIds. Gives write-access to the OrgUnit and all its descendants
     *
     * @return
     */
    @BsonProperty(_WH) // Required here otherwise property will be saved in DB with capital H
    @JsonbProperty(_WH)
    public Set<String> getWH() {
        return _wh;
    }


    @BsonProperty(_WH) // Required here otherwise property will be saved in DB with capital H
    @JsonbProperty(_WH)
    public void setWH(Set<String> wh) {
        _wh.clear();
        if (wh != null) {
            _wh.addAll(wh);
        }
    }


    /**
     * Returns set of entities with read-access
     *
     * @return
     */
    public Set<String> getR() {
        return _r;
    }


    public void setR(Set<String> r) {
        _r.clear();
        if (r != null) {
            _r.addAll(r);
        }
    }


    /**
     * Returns set of entities with read-access (hierarchical).
     * Commonly used with OrgUnit systemIds. Gives read-access to the OrgUnit and all its descendants
     *
     * @return
     */
    @BsonProperty(_RH) // Required here otherwise property will be saved in DB with capital H
    @JsonbProperty(_RH)
    public Set<String> getRH() {
        return _rh;
    }


    @BsonProperty(_RH) // Required here otherwise property will be saved in DB with capital H
    @JsonbProperty(_RH)
    public void setRH(Set<String> rh) {
        _rh.clear();
        if (rh != null) {
            _rh.addAll(rh);
        }
    }


    //
    // Helper getters/setters for optimizing integration with UI ----------------------------------
    //
    /**
     * Only for usage via UI
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public List<String> getWList() {
        return new ArrayList<>(getW());
    }


    /**
     * Only for usage via UI
     *
     * @param w
     */
    @BsonIgnore
    @JsonbTransient
    public void setWList(List<String> w) {
        setW(new HashSet<>(w));
    }


    /**
     * Only for usage via UI
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public List<String> getWHList() {
        return new ArrayList<>(getWH());
    }


    /**
     * Only for usage via UI
     *
     * @param wh
     */
    @BsonIgnore
    @JsonbTransient
    public void setWHList(List<String> wh) {
        setWH(new HashSet<>(wh));
    }


    /**
     * Only for usage via UI
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public List<String> getRList() {
        return new ArrayList<>(getR());
    }


    /**
     * Only for usage via UI
     *
     * @param r
     */
    @BsonIgnore
    @JsonbTransient
    public void setRList(List<String> r) {
        setR(new HashSet<>(r));
    }


    /**
     * Only for usage via UI
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public List<String> getRHList() {
        return new ArrayList<>(getRH());
    }


    /**
     * Only for usage via UI
     *
     * @param rh
     */
    @BsonIgnore
    @JsonbTransient
    public void setRHList(List<String> rh) {
        setRH(new HashSet<>(rh));
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(_w);
        hash = 97 * hash + Objects.hashCode(_wh);
        hash = 97 * hash + Objects.hashCode(_r);
        hash = 97 * hash + Objects.hashCode(_rh);
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
        final Acl other = (Acl) obj;
        if (!SetUtils.isEqualSet(_w, other._w)) {
            return false;
        }
        if (!SetUtils.isEqualSet(_wh, other._wh)) {
            return false;
        }
        if (!SetUtils.isEqualSet(_r, other._r)) {
            return false;
        }
        if (!SetUtils.isEqualSet(_rh, other._rh)) {
            return false;
        }
        return true;
    }

}
