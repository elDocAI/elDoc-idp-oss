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
package ai.eldoc.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Simple String Hasher Class
 */
public class StringHasher {

    //
    // Constructor --------------------------------------------------------------------------------
    //
    private StringHasher() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Hashes given string with the given number of iterations using SHA-256 and returns its byte array
     *
     * @param input
     * @param iterations
     * @return byte array representation of the hashed value
     */
    public static byte[] hash(String input, int iterations) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = input.getBytes();
            for (int i = 0; i < iterations; i++) {
                hash = digest.digest(hash);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
