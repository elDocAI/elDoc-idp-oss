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
package ai.eldoc.exceptions.handler;

import jakarta.faces.context.ExceptionHandler;
import org.primefaces.application.exceptionhandler.ExceptionInfo;
import org.primefaces.application.exceptionhandler.PrimeExceptionHandler;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * ExceptionHandler implementation, that support ID generation for synchronising
 * messages, displayed to user, and server log stacktraces
 */
public class ElDocExceptionHandler extends PrimeExceptionHandler {

    private static final Logger LOG = Logger.getLogger(ElDocExceptionHandler.class.getName());


    //
    // Constructor --------------------------------------------------------------------------------
    //  
    public ElDocExceptionHandler(ExceptionHandler wrapped) {
        super(wrapped);
    }


    /**
     * Disable logging, as stacktrace is already logged in {@link #createExceptionInfo(java.lang.Throwable) }
     *
     * @param rootCause
     */
    @Override
    protected void logException(Throwable rootCause) {
    }


    /**
     * Creates {@link ExceptionInfo} and generates new {@link UUID} for provided {@link Throwable}<br>
     * Generated ID and stacktrace are logged to server log, therefore logging in {@link #logException(java.lang.Throwable) }
     * should be disabled
     *
     * @param rootCause
     * @return
     * @throws IOException
     */
    @Override
    protected ExceptionInfo createExceptionInfo(Throwable rootCause) throws IOException {
        ExceptionInfo exInfo = super.createExceptionInfo(rootCause);
        String traceId = UUID.randomUUID().toString();
        LOG.log(Level.SEVERE, "Trace ID: " + traceId + " | " + rootCause.getMessage(), rootCause);
        exInfo.setMessage(traceId);
        return exInfo;
    }

}
