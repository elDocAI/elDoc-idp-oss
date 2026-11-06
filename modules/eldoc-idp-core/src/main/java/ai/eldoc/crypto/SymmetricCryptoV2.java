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
import org.apache.commons.codec.DecoderException;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import static java.text.MessageFormat.format;


/**
 * Symmetric Crypto (AES) Utils Class
 */
public final class SymmetricCryptoV2 {

    private final static String CIPHER_ALG_BASE = "AES";

    // https://rules.sonarsource.com/java/RSPEC-5542
    private final static String CIPHER_ALG = "AES/GCM/NoPadding"; // Compliant


    //
    // Constructors -------------------------------------------------------------------------------
    //
    private SymmetricCryptoV2() {
        // no public constructor
    }


    //
    // Mathods ------------------------------------------------------------------------------------
    //
    /**
     * Encrypts given clearText byte-array with provided encryption key
     *
     * @param key
     * @param clearText
     * @return
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public static byte[] encrypt(byte[] key, byte[] clearText) throws InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
        byte[] iv = new byte[12]; // See more why 12: https://crypto.stackexchange.com/questions/41601/aes-gcm-recommended-iv-size-why-12-bytes
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(iv);

        SecretKey secretKey = new SecretKeySpec(key, CIPHER_ALG_BASE);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv); // 128 bit auth tag length

        Cipher cipher = Cipher.getInstance(CIPHER_ALG);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        byte[] cipherText = cipher.doFinal(clearText);

        ByteBuffer cipherByteBuffer = ByteBuffer.allocate(4 + iv.length + cipherText.length);
        cipherByteBuffer.putInt(iv.length);
        cipherByteBuffer.put(iv);
        cipherByteBuffer.put(cipherText);

        return cipherByteBuffer.array();
    }


    /**
     * Decrypts given clearText byte-array with provided encryption key
     *
     * @param key
     * @param encText
     * @return
     * @throws DecoderException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public static byte[] decrypt(byte[] key, byte[] encText) throws DecoderException, InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(encText);
        int ivLength = byteBuffer.getInt();

        if (ivLength < 12 || ivLength > 64) { // check input parameter
            throw new ElDocRuntimeException(format("Invalid IV length: {0}bits, should be in the range [{1}, {2}]", ivLength * 8, 12 * 8, 64 * 8));
        }

        byte[] iv = new byte[ivLength];
        byteBuffer.get(iv);

        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        Cipher cipher = Cipher.getInstance(CIPHER_ALG);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, CIPHER_ALG_BASE), new GCMParameterSpec(128, iv));
        return cipher.doFinal(cipherText);
    }


    /**
     * Generates encryption 128-bit key
     *
     * @return
     */
    public static byte[] generateKey128bit() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] key = new byte[16];
        secureRandom.nextBytes(key);
        return key;
    }


    /**
     * Generates encryption 256-bit key
     *
     * @return
     */
    public static byte[] generateKey256bit() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] key = new byte[32];
        secureRandom.nextBytes(key);
        return key;
    }

}
