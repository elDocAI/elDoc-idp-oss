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

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;


/**
 * Asymmetric Crypto Utils based on Ed25519
 */
public final class AsymmetricCryptoV2 {

    private static final String CIPHER_INSTANCE = "Ed25519";


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private AsymmetricCryptoV2() {
        // no public constructor
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    // ----- Ed25519 sign/verify -----
    /**
     * Signs given message and returns sing
     *
     * @param message
     * @param privateKey
     * @return signature bytes (always 64 bytes as per Ed25519 spec)
     * @throws GeneralSecurityException
     */
    public static byte[] sign(byte[] message, PrivateKey privateKey) throws GeneralSecurityException {
        Signature s = Signature.getInstance(CIPHER_INSTANCE);
        s.initSign(privateKey);
        s.update(message);
        return s.sign(); // always 64 bytes as per Ed25519 spec
    }


    /**
     * Verifies given message and its signature against given public key
     *
     * @param message
     * @param signature
     * @param publicKey
     * @return
     * @throws GeneralSecurityException
     */
    public static boolean verify(byte[] message, byte[] signature, PublicKey publicKey) throws GeneralSecurityException {
        Signature s = Signature.getInstance(CIPHER_INSTANCE);
        s.initVerify(publicKey);
        s.update(message);
        return s.verify(signature);
    }

    // ----- Key load/save helpers (X.509 public, PKCS#8 private) -----

    /**
     * Exports public key (X.509/SPKI) to be encoded as string
     *
     * @param publicKey
     * @return
     */
    public static byte[] exportPublicKey(PublicKey publicKey) {
        return publicKey.getEncoded(); // X.509/SPKI
    }


    /**
     * Exports private key (PKCS#8) to be encoded as string
     *
     * @param privateKey
     * @return
     */
    public static byte[] exportPrivateKey(PrivateKey privateKey) {
        return privateKey.getEncoded(); // PKCS#8
    }


    /**
     * Imports public key from encoded string
     *
     * @param x509SpkiBytes encoded key
     * @return
     * @throws GeneralSecurityException
     */
    public static PublicKey importPublicKey(byte[] x509SpkiBytes) throws GeneralSecurityException {
        KeyFactory kf = KeyFactory.getInstance(CIPHER_INSTANCE);
        return kf.generatePublic(new X509EncodedKeySpec(x509SpkiBytes));
    }


    /**
     * Imports private key from encoded string
     *
     * @param pkcs8Bytes encoded key
     * @return
     * @throws GeneralSecurityException
     */
    public static PrivateKey importPrivateKey(byte[] pkcs8Bytes) throws GeneralSecurityException {
        KeyFactory kf = KeyFactory.getInstance(CIPHER_INSTANCE);
        return kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8Bytes));
    }


    public static KeyPair generateKeys() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(CIPHER_INSTANCE);
        KeyPair keyPair = kpg.generateKeyPair();
        return keyPair;
    }

}
