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
package ai.eldoc.crypto;

import ai.eldoc.ElDocRuntimeException;
import org.apache.commons.codec.binary.Hex;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.spec.KeySpec;
import java.util.Arrays;
import static java.text.MessageFormat.format;


/**
 * Symmetric Cryptography Class (V1)
 */
@Deprecated
public class SymmetricCryptoV1 {

    private final static int SALT_LEN = 8;
    private final static int IV_LENGTH = 16;


    //
    // Mathods ------------------------------------------------------------------------------------
    //
    /**
     * Encrypts provided string with given key and returns its encrypted value in HEX-string representation
     *
     * @param key
     * @param clearText
     * @return
     * @throws Exception
     */
    public static String doEncHex(final String key, final String clearText) throws Exception {
        return Hex.encodeHexString(encrypt(getKey(key.toCharArray()), clearText.getBytes(StandardCharsets.UTF_8)), false);
    }


    /**
     * Decrypts provided HEX-encrypted string with given key
     *
     * @param key
     * @param encTextHex
     * @return
     * @throws Exception
     */
    public static String doDecHex(final String key, final String encTextHex) throws Exception {
        return new String(decrypt(getKey(key.toCharArray()), Hex.decodeHex(encTextHex)), StandardCharsets.UTF_8);
    }


    //
    // Private Methods ----------------------------------------------------------------------------
    //
    /**
     * Derive the key, given password and salt
     *
     * @param password
     * @return
     * @throws Exception
     */
    private static byte[] getKey(char[] keyChar) throws Exception {
        byte[] salt = new byte[SALT_LEN];
        Arrays.fill(salt, (byte) 0); // We effectively do not use salt (due to backward compatibility with v3.X)
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(keyChar, salt, 65536, 128);
        SecretKey sKey = factory.generateSecret(spec);
        return sKey.getEncoded();
    }


    /**
     * Encrypt the text
     * key secret
     *
     * @param clearText
     * @return
     * @throws Exception
     */
    private static byte[] encrypt(final byte[] key, final byte[] clearText) throws Exception {
        // https://rules.sonarsource.com/java/RSPEC-5542
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        AlgorithmParameters params = cipher.getParameters();
        byte[] iv = (params.getParameterSpec(IvParameterSpec.class)).getIV();
        if (iv.length != IV_LENGTH) {
            throw new ElDocRuntimeException(format("Invalid IV length: {0}bits instead of required 128bits", (iv.length * 8)));
        }
        byte[] cipherText = cipher.doFinal(clearText);
        ByteBuffer cipherByteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
        cipherByteBuffer.put(iv);
        cipherByteBuffer.put(cipherText);
        return cipherByteBuffer.array();
    }


    /**
     * Decrypt the text, given derived key and initialization vector
     *
     * @param key
     * @param encText
     * @return
     * @throws Exception
     */
    private static byte[] decrypt(final byte[] key, final byte[] encText) throws Exception {
        // https://rules.sonarsource.com/java/RSPEC-5542
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        ByteBuffer byteBuffer = ByteBuffer.wrap(encText);
        byte[] iv = new byte[IV_LENGTH];
        byteBuffer.get(iv);
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);
        return cipher.doFinal(cipherText);
    }

}
