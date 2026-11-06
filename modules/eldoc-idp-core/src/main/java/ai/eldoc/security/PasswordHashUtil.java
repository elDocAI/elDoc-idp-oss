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

import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.lang.codec.Base64;
import org.apache.shiro.lang.util.ByteSource;
import java.util.Objects;


/**
 */
public class PasswordHashUtil {

    private final RandomNumberGenerator _rng = new SecureRandomNumberGenerator();
    private ByteSource _saltBytes;
    private int _iterations = 1024;


    /**
     * Empty constructor for creating instance of Class
     */
    public PasswordHashUtil() {

    }


    /**
     * Constructor which creates object instance with given number of iterations for hashing password
     *
     * @param iterations
     */
    public PasswordHashUtil(int iterations) {
        _iterations = iterations;
    }


    /**
     * Hashes plain password (with multiple iterations) based on generated salt<br>
     * IMPORTANT: salt to be retrieved as well and saved together with password in the model
     *
     * @param plainPassword
     * @return hashed password as base64 string
     */
    public String getPasswordHash(final String plainPassword) {
        // Uses Shiro: https://shiro.apache.org/realm.html#Realm-%7B%7BSaltedAuthenticationInfo%7D%7D
        // Generate salt
        _saltBytes = _rng.nextBytes(32);
        // Now hash the plain-text password with the random salt and multiple
        // iterations and then Base64-encode the value (requires less space than Hex):
        return new Sha256Hash(plainPassword, _saltBytes, _iterations).toBase64();
    }


    /**
     * Checks, whether provided password matches previously generated hash
     *
     * @param iterations Number of iterations, that was used to generate hash
     * @param salt Salt string (base64-encoded), used for hash generation
     * @param hash Known password hash
     * @param password Password, that needs to be checked
     * @return {@code true}, if provided password matches hash, or {@code false} otherwise
     */
    public static boolean isPasswordMatches(int iterations, String salt, String hash, String password) {
        ByteSource saltBytes = ByteSource.Util.bytes(Base64.decode(salt));
        String newPasswordHash = new Sha256Hash(password, saltBytes, iterations).toBase64();
        return Objects.equals(hash, newPasswordHash);
    }


    /**
     * Returns base64 string with generated salt (that was used for hashing the password)
     *
     * @return
     */
    public String getSalt() {
        return _saltBytes.toBase64();
    }


    /**
     * Returns the number of iterations for hashing the password
     *
     * @return
     */
    public int getIterations() {
        return _iterations;
    }
}
