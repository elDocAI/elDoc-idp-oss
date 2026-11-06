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

import ai.eldoc.exceptions.BadArgumentException;
import io.seruco.encoding.base62.Base62;
import org.apache.commons.lang3.ObjectUtils;
import org.bson.types.ObjectId;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;


/**
 * Model represents an unambiguous reference to the DB item (document, file, etc.) or its revision (in case of files) in
 * the specific DB collection, in the specific DB of the specific tenant
 */
public class DbWebRef implements Serializable {

    @Serial
    private static final long serialVersionUID = 7112288672265159552L;

    private static final String CIPHER_ALG = "Blowfish";
    private static final String CIPHER_ALG_SPEC = "Blowfish/ECB/PKCS5Padding";
    private static final byte TAG_TYPE_DBREF = 0b000_00_000; // Where: Type=000 (ref to the item), Mode=00 (just reserved), Version = 000
    private static final byte TAG_TYPE_REVDBREF = 0b001_00_000; // Where: Type=001 (ref to the item's rev), Mode=00 (just reserved), Version = 000

    public static final String VALIDATION_PATTERN_BASE62_STR = "^[0-9a-zA-Z]{20,100}$"; // 8hN09MdEuUrcdvujiMTn9AFDEbKcEK4Oyu | 1069xrTDyfdevpL9SPWJ6U9bMLMUlbaJ8T7XmNzCX1dbEoE8BcIEfMA4 length vary due to tenantId
    public static final String VALIDATION_PATTERN_STR = "^\\w{1,20}-\\d{1,3}-\\p{XDigit}{24}(-\\p{XDigit}{24})?-\\d{1,3}$"; // tid1000-206-6376f38b9f293412c4ad73ac-0 | tid1000-206-6376f38b9f293412c4ad73ac-6376f38b9f293412c4ad73ab-32
    private static final Pattern VALIDATION_PATTERN = Pattern.compile(VALIDATION_PATTERN_STR);

    private final byte[] _tenantId;
    private final byte _colId; // Stores values as -128...127, as such it has to be converted to UnsignedByte before output via toString(), etc.
    private final byte[] _id;
    private final byte[] _revId;


    //
    // Constructor --------------------------------------------------------------------------------
    //


    /**
     * Constructs DBWebRef to the item in the DB
     *
     * @param tenantId string value of tenantId
     * @param colId id of the collection
     * @param id HEX-string representation of the ObjectId
     */
    public DbWebRef(String tenantId, byte colId, String id) {
        this(tenantId.getBytes(StandardCharsets.US_ASCII), colId, new ObjectId(id).toByteArray(), null);
    }


    /**
     * Constructs DBWebRef to the item and its revision in the DB
     *
     * @param tenantId string value of tenantId
     * @param colId id of the collection
     * @param id HEX-string representation of the ObjectId
     * @param revId HEX-string representation of the ObjectId
     */
    public DbWebRef(String tenantId, byte colId, String id, String revId) {
        this(tenantId.getBytes(StandardCharsets.US_ASCII), colId, new ObjectId(id).toByteArray(), new ObjectId(revId).toByteArray());
    }


    private DbWebRef(byte[] tenantId, byte colId, byte[] id, byte[] revId) {
        if (!ObjectUtils.allNotNull(tenantId, id) || colId == (byte) 0) {
            String errorsReport = null;
            if (tenantId == null) {
                errorsReport = "tenantId";
            }
            if (colId == (byte) 0) {
                errorsReport = (errorsReport == null ? "colId" : errorsReport + ", colId");
            }
            if (id == null) {
                errorsReport = (errorsReport == null ? "id" : errorsReport + ", id");
            }
            throw new BadArgumentException("All argumetns must have non-NULL or non-0 values: " + errorsReport);
        }
        _tenantId = tenantId;
        _colId = colId;
        _id = id;
        _revId = revId;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Constructs revisioned DbWebRef (DbWebRef with updated revisionId) from the current DbWebRef
     *
     * @param revId HEX-string representation of the ObjectId
     * @return
     */
    public DbWebRef toRevisionedDbWebRef(String revId) {
        return new DbWebRef(_tenantId, _colId, _id, new ObjectId(revId).toByteArray());
    }


    /**
     * Constructs DbWebRef from the encrypted string representation of DbWebRef (Base62-encoded string of encrypted with Blowfish bytes)
     *
     * @param taggedEncryptedPayloadBytesBase62
     * @param secret
     * @return
     */
    public static DbWebRef fromCipher(String taggedEncryptedPayloadBytesBase62, byte[] secret) throws GeneralSecurityException {
        // Decode encrypted DbWebRef
        byte[] taggedEncryptedDbWebRefStr = taggedEncryptedPayloadBytesBase62.getBytes(StandardCharsets.US_ASCII);
        byte tag = getTagFromTaggetEncryptedPayloadBytes(Base62.createInstance().decode(taggedEncryptedDbWebRefStr));
        byte[] encryptedDbWebRef = getUntaggetEncryptedPayloadBytes(Base62.createInstance().decode(taggedEncryptedDbWebRefStr));

        // Decrypt encrypted DbWebRef
        Cipher cipher = Cipher.getInstance(CIPHER_ALG_SPEC);
        Key secretKey = new SecretKeySpec(secret, CIPHER_ALG);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedDbWebRef = cipher.doFinal(encryptedDbWebRef);

        return DbWebRef.fromBytes(decryptedDbWebRef, tag);
    }


    /**
     * Returns encrypted with Blowfish string representation of this DbWebRef (encoded in Base62)
     *
     * @param secret
     * @return
     */
    public String toCipher(byte[] secret) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_ALG_SPEC);
        Key secretKey = new SecretKeySpec(secret, CIPHER_ALG);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedDbWebRef = cipher.doFinal(toUntaggedBytes());
        byte[] taggedEncryptedDbWebRefStr = Base62.createInstance().encode(getTaggetCipherBytes(encryptedDbWebRef));
        return new String(taggedEncryptedDbWebRefStr, StandardCharsets.US_ASCII);
    }


    /**
     * Constructs DbWebRef from the plain string representation of DbWebRef (e.g. tid1000-128-6376f38b9f293412c4ad73ac-0 or tid1000-206-6376f38b9f293412c4ad73ac-6376f38b9f293412c4ad73ab-32)<br>
     *
     * @param dbWebRefStr
     * @return
     * @throws BadArgumentException in case given string has a not valid format
     */
    public static DbWebRef fromString(String dbWebRefStr) throws BadArgumentException {
        if (!isValid(dbWebRefStr)) {
            throw new BadArgumentException("DbWebRef format is not valid (has to have format: XXX-ddd-NNNNNNN-d or XXX-ddd-NNNNNNN-YYYYYYY-d), instead was: " + dbWebRefStr);
        }

        String[] dbWebRefArr = dbWebRefStr.split("-");
        switch (dbWebRefArr.length) {
            case 4:
                // Format: XXX-ddd-NNNNNNN-d
                return new DbWebRef(dbWebRefArr[0], Integer.valueOf(dbWebRefArr[1]).byteValue(), dbWebRefArr[2]);
            case 5:
                // Format: XXX-ddd-NNNNNNN-YYYYYYY-d
                return new DbWebRef(dbWebRefArr[0], Integer.valueOf(dbWebRefArr[1]).byteValue(), dbWebRefArr[2], dbWebRefArr[3]);
            default:
                // Never should get here
                throw new BadArgumentException("DbWebRef format is not valid after split (has to have format: XXX-ddd-NNNNNNN-d or XXX-ddd-NNNNNNN-YYYYYYY-d), instead was: " + dbWebRefStr);
        }
    }


    /**
     * Checks if the given string representing a plain DbWebRef (e.g. tid1000-128-6376f38b9f293412c4ad73ac-0 or tid1000-206-6376f38b9f293412c4ad73ac-6376f38b9f293412c4ad73ab-32)
     * has a valid format (using pattern-based check)
     *
     * @param dbWebRefStr string
     * @return
     */
    public static boolean isValid(String dbWebRefStr) {
        return dbWebRefStr != null && VALIDATION_PATTERN.matcher(dbWebRefStr).matches();
    }


    /**
     * Generates secure 448bit key to be used with DbWebRef
     *
     * @return
     */
    public static byte[] generateKey() {
        byte[] key = new byte[448 / 8];
        new SecureRandom().nextBytes(key);
        return key;
    }


    /**
     * Returns untagged byte array representing this DbWebRef
     *
     * @return
     */
    private byte[] toUntaggedBytes() {
        int allocateSize = 12 + 1 + _tenantId.length; // This is: 12(_id.length) + 1 + tid.lenght (remaining)
        if (_revId != null) {
            allocateSize = 12 + 12 + 1 + _tenantId.length; // This is: 12(_id.length) + 12(_revId.length) + 1 + tid.lenght (remaining)
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(allocateSize);
        byteBuffer.put(_id);
        if (_revId != null) {
            byteBuffer.put(_revId);
        }
        byteBuffer.put(_colId);
        byteBuffer.put(_tenantId);
        return byteBuffer.array();
    }


    /**
     * Returns tagged byte array representing this DbWebRef
     *
     * @return
     */
    public byte[] toTaggedBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(toUntaggedBytes().length + 1); // TAG comes last
        byteBuffer.put(toUntaggedBytes());

        if (_revId == null) {
            byteBuffer.put(TAG_TYPE_DBREF);
        } else {
            byteBuffer.put(TAG_TYPE_REVDBREF);
        }

        return byteBuffer.array();
    }


    /**
     * Constructs instance from tagged bytes representing DBWebRef
     *
     * @param dbWebRefTaggedBytes
     * @return
     */
    public static DbWebRef fromTaggedBytes(byte[] dbWebRefTaggedBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(dbWebRefTaggedBytes);
        byte[] dbWebRefBytes = new byte[dbWebRefTaggedBytes.length - 1];
        byteBuffer.get(dbWebRefBytes);
        byte tag = byteBuffer.get();
        return DbWebRef.fromBytes(dbWebRefBytes, tag);
    }


    /**
     * Constructs instance from bytes representing DBWebRef and tag
     *
     * @param dbWebRefBytes
     * @param tag
     * @return
     */
    private static DbWebRef fromBytes(byte[] dbWebRefBytes, byte tag) {
        // Read parts of DbWebRef
        ByteBuffer byteBuffer = ByteBuffer.wrap(dbWebRefBytes);

        byte[] id = new byte[12];
        byteBuffer.get(id);

        if (Byte.compare(TAG_TYPE_DBREF, tag) == 0) {

            byte colId = byteBuffer.get();

            byte[] tenantId = new byte[byteBuffer.remaining()];
            byteBuffer.get(tenantId);

            return new DbWebRef(tenantId, colId, id, null);

        } else if (Byte.compare(TAG_TYPE_REVDBREF, tag) == 0) {

            byte[] revId = new byte[12];
            byteBuffer.get(revId);

            byte colId = byteBuffer.get();

            byte[] tenantId = new byte[byteBuffer.remaining()];
            byteBuffer.get(tenantId);

            return new DbWebRef(tenantId, colId, id, revId);

        } else {
            throw new BadArgumentException("Bad tag rpovided: " + tag);
        }
    }


    /**
     * Adds TAG byte to the end of the encrypted byteArray representing DbWebRef and returns resulting tagged encrypted byteArray
     *
     * @param encryptedPayloadBytes
     * @return
     */
    private byte[] getTaggetCipherBytes(byte[] encryptedPayloadBytes) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(encryptedPayloadBytes.length + 1); // TAG comes last
        byteBuffer.put(encryptedPayloadBytes);
        if (_revId == null) {
            byteBuffer.put(TAG_TYPE_DBREF);
        } else {
            byteBuffer.put(TAG_TYPE_REVDBREF);
        }
        return byteBuffer.array();
    }


    /**
     * Retrieves tag from the tagged encrypted payload byte array
     *
     * @param taggedEncryptedPayloadBytes
     * @return
     */
    private static byte getTagFromTaggetEncryptedPayloadBytes(byte[] taggedEncryptedPayloadBytes) {
        return taggedEncryptedPayloadBytes[taggedEncryptedPayloadBytes.length - 1];
    }


    /**
     * Returns untagged encrypted payload byte array
     *
     * @param taggedEncryptedPayloadBytes
     * @return
     */
    private static byte[] getUntaggetEncryptedPayloadBytes(byte[] taggedEncryptedPayloadBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(taggedEncryptedPayloadBytes);
        byte[] encryptedPayload = new byte[taggedEncryptedPayloadBytes.length - 1];
        byteBuffer.get(encryptedPayload);
        return encryptedPayload;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getTenantId() {
        return new String(_tenantId, StandardCharsets.US_ASCII);
    }


    public byte getColId() {
        return _colId;
    }


    public byte[] getId() {
        return _id;
    }


    public String getIdAsString() {
        if (_id == null) {
            return null;
        }
        return HexFormat.of().formatHex(_id);
    }


    public byte[] getRevId() {
        return _revId;
    }


    public String getRevIdAsString() {
        if (_revId == null) {
            return null;
        }
        return HexFormat.of().formatHex(_revId);
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        if (_revId == null) {
            return getTenantId() +
                    "-" +
                    Byte.toUnsignedInt(_colId) +
                    "-" +
                    getIdAsString() +
                    "-" +
                    Byte.toUnsignedInt(TAG_TYPE_DBREF);
        } else {
            return getTenantId() +
                    "-" +
                    Byte.toUnsignedInt(_colId) +
                    "-" +
                    getIdAsString() +
                    "-" +
                    getRevIdAsString() +
                    "-" +
                    Byte.toUnsignedInt(TAG_TYPE_REVDBREF);
        }
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DbWebRef dbWebRef = (DbWebRef) o;
        return _colId == dbWebRef._colId && Objects.deepEquals(_tenantId, dbWebRef._tenantId) && Objects.deepEquals(_id, dbWebRef._id) && Objects.deepEquals(_revId, dbWebRef._revId);
    }


    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(_tenantId), _colId, Arrays.hashCode(_id), Arrays.hashCode(_revId));
    }
}
