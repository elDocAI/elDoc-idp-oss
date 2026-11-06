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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.model.PropertyAwareModel;
import ai.eldoc.util.I18n;
import jakarta.json.bind.annotation.JsonbTransient;
import org.apache.commons.codec.binary.Base32;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.security.*;
import java.util.*;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;


/**
 * Application Config which is defined per-tenant
 */
public class AppConfig extends Config implements PropertyAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = -5269827217228769161L;

    private static final String _SYSTEM_URL = "systemUrl";
    private static final String _INSTANCE_KEY = "instanceKey";
    private static final String _SYSTEM_TIMEZONE = "systemTimezone";
    private static final String _SYSTEM_DATE_FORMAT = "systemDateFormat";
    private static final String _SYSTEM_TIME_FORMAT = "systemTimeFormat";
    private static final String _LANGS = "langs";
    private static final String _MFA_REQUIRED = "mfaRequired";

    public static final String SYSTEM_URL_DEFAULT = "http://localhost";
    public static final int FILES_FORMAT_OPENDOCUMENT = 0;
    public static final int FILES_FORMAT_MSOFFICE = 1;

    private String _systemUrl;
    private String _systemTimezone;
    private String _systemDateFormat;
    private String _systemTimeFormat;
    private String _instanceKey;
    private final List<String> _langs;
    private boolean _mfaRequired;
    private int _filesFormat;
    private final Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    protected AppConfig() {
        _systemUrl = SYSTEM_URL_DEFAULT;
        _systemTimezone = "UTC";
        _systemDateFormat = "dd.MM.yyyy";
        _systemTimeFormat = "HH:mm";
        _langs = new ArrayList<>();
        _langs.addAll(I18n.LANG_SYSTEM_CODES_ALL);
        _filesFormat = FILES_FORMAT_OPENDOCUMENT;
        _properties = new HashMap<>();
        _instanceKey = getNewInstanceKey();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static AppConfig createConfig() {
        AppConfig appConfig = new AppConfig();
        appConfig.setInstanceKey(getNewInstanceKey());
        return appConfig;
    }


    private static String getNewInstanceKey() {
        byte[] instanceKey = new byte[32];
        new SecureRandom().nextBytes(instanceKey);
        return Base64.getEncoder().encodeToString(instanceKey);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    public String getSystemUrl() {
        return _systemUrl;
    }


    public void setSystemUrl(String systemUrl) {
        _systemUrl = systemUrl;
    }


    public String getSystemTimezone() {
        return _systemTimezone;
    }


    public void setSystemTimezone(String systemTimezone) {
        _systemTimezone = systemTimezone;
    }


    public String getSystemDateFormat() {
        return _systemDateFormat;
    }


    public void setSystemDateFormat(String systemDateFormat) {
        _systemDateFormat = systemDateFormat;
    }


    public String getSystemTimeFormat() {
        return _systemTimeFormat;
    }


    public void setSystemTimeFormat(String systemTimeFormat) {
        _systemTimeFormat = systemTimeFormat;
    }


    /**
     * Sets instance key as Base64-encoded string<br>
     *
     * <strong>IMPORTANT:</strong> should not be used across project, only to be used by Mongo POJO logic
     *
     * @param instanceKeyBase64
     */
    public void setInstanceKey(String instanceKeyBase64) {
        _instanceKey = instanceKeyBase64;
    }


    /**
     * Returns instance key as Base64-encoded string<br><br>
     *
     * <strong>IMPORTANT:</strong> should not be used across project, only to be used by Mongo POJO logic.<br><br>
     *
     * <strong>NOTE:</strong> Represents key byte-array as Base64-encoded string, as such it has to be decoded from Base64-encoded string
     * in order to get a key byte-array in the following way: {@code Base64.getDecoder().decode(getInstanceKey())}.
     * For this there is a separate getter available: {@link #getInstanceKeyByteArray()}<br><br>
     * It is used mostly incorrectly across the project by retrieving key as {@code getInstanceKey().toCharArray()}, what is wrong approach.
     *
     * @return
     */
    public String getInstanceKey() {
        return _instanceKey;
    }


    /**
     * Returns instance key as ByteArray
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public byte[] getInstanceKeyByteArray() {
        return Base64.getDecoder().decode(getInstanceKey());
    }


    /**
     * Returns Instance ID (Base32 string) which is a 96bit fingerprint of the Instance Key (Fingerprint = Truncate(SHA-256(Instance Key)))
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public String getInstanceId() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(getInstanceKeyByteArray());
            byte[] fingerprintBytes = Arrays.copyOf(hash, 12); // Truncate to 12 bytes (96 bits) in order to get a key fingerprint
            return new Base32().encodeAsString(fingerprintBytes).replace("=", "");
        } catch (NoSuchAlgorithmException ex) {
            throw new ElDocRuntimeException(ex); // This should not happen
        }
    }


    public List<String> getLangs() {
        return _langs;
    }


    public void setLangs(List<String> langs) {
        _langs.clear();
        emptyIfNull(langs).stream()
                .distinct()
                .filter(I18n.LANG_SYSTEM_CODES_ALL::contains)
                .forEachOrdered(_langs::add);
    }


    @BsonProperty(_MFA_REQUIRED)
    public boolean isMfaRequired() {
        return _mfaRequired;
    }


    @BsonProperty(_MFA_REQUIRED)
    public void setMfaRequired(boolean mfaRequired) {
        _mfaRequired = mfaRequired;
    }


    public int getFilesFormat() {
        return _filesFormat;
    }


    public void setFilesFormat(int filesFormat) {
        _filesFormat = filesFormat;
    }


    @Override
    @BsonProperty(_PROPERTIES)
    public Map<String, Object> getProperties() {
        return _properties;
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AppConfig appConfig = (AppConfig) o;
        return _mfaRequired == appConfig._mfaRequired && _filesFormat == appConfig._filesFormat
                && Objects.equals(_systemUrl, appConfig._systemUrl) && Objects.equals(_systemTimezone, appConfig._systemTimezone)
                && Objects.equals(_systemDateFormat, appConfig._systemDateFormat) && Objects.equals(_systemTimeFormat, appConfig._systemTimeFormat)
                && Objects.equals(_instanceKey, appConfig._instanceKey)
                && Objects.equals(_langs, appConfig._langs) && Objects.equals(_properties, appConfig._properties);
    }


    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _systemUrl, _systemTimezone, _systemDateFormat, _systemTimeFormat,
                _instanceKey, _langs, _mfaRequired, _filesFormat, _properties);
    }
}
