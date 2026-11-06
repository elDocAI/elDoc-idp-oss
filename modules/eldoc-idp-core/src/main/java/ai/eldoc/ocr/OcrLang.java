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
package ai.eldoc.ocr;

import java.io.Serializable;
import java.util.Objects;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * OCR Language model
 */
public class OcrLang implements Serializable {

    private static final long serialVersionUID = 8460470694459712401L;

    private final static String _ENGINE = "engine";
    private final static String _CODE = "code";
    private final static String _NAME = "name";
    private final static String _NAME_NATIVE = "nameNative";
    private final static String _ENABED = "enabled";

    private String _engine;
    private String _code;
    private String _name; // IMPORTANT: languages are linked between different engines by name
    private String _nameNative;
    private boolean _enabled;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public OcrLang(String engine, String code) {
        this(engine, code, code, null, true);
    }


    public OcrLang(String engine, String code, String name, String nameNative, boolean enabled) {
        _engine = engine;
        _code = code;
        _name = name;
        _nameNative = nameNative;
        _enabled = enabled;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static OcrLang initAsNotFound(String engine, String code) {
        return new OcrLang(engine, code, code, null, false);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getEngine() {
        return _engine;
    }


    public void setEngine(String engine) {
        _engine = engine;
    }


    public String getCode() {
        return _code;
    }


    public void setCode(String code) {
        _code = code;
    }


    public String getName() {
        return _name;
    }


    public String getNameUI() {
        StringBuilder uiLabel = new StringBuilder();

        if (isNotBlank(getName())) {
            uiLabel.append(getName());
        } else {
            // Fall back to code only
            uiLabel.append(getCode());
        }

        if (isNotBlank(getNameNative())) {
            uiLabel.append(" (");
            uiLabel.append(getNameNative());
            uiLabel.append(")");
        }

        return uiLabel.toString();
    }


    public void setName(String name) {
        _name = name;
    }


    public String getNameNative() {
        return _nameNative;
    }


    public void setNameNative(String nameNative) {
        _nameNative = nameNative;
    }


    public boolean isEnabled() {
        return _enabled;
    }


    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + Objects.hashCode(_engine);
        hash = 41 * hash + Objects.hashCode(_code);
        hash = 41 * hash + Objects.hashCode(_name);
        hash = 41 * hash + Objects.hashCode(_nameNative);
        hash = 41 * hash + (_enabled ? 1 : 0);
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
        final OcrLang other = (OcrLang) obj;
        if (_enabled != other._enabled) {
            return false;
        }
        if (!Objects.equals(_engine, other._engine)) {
            return false;
        }
        if (!Objects.equals(_code, other._code)) {
            return false;
        }
        if (!Objects.equals(_name, other._name)) {
            return false;
        }
        if (!Objects.equals(_nameNative, other._nameNative)) {
            return false;
        }
        return true;
    }

}
