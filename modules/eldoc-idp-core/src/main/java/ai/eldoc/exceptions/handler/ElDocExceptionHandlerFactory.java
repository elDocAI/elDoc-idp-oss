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
import jakarta.faces.context.ExceptionHandlerFactory;
import org.primefaces.application.exceptionhandler.PrimeExceptionHandlerFactory;


/**
 * {@link ExceptionHandlerFactory} implementation, that provides {@link ElDocExceptionHandler}
 * to handle exceptions
 */
public class ElDocExceptionHandlerFactory extends PrimeExceptionHandlerFactory {

    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ElDocExceptionHandlerFactory(ExceptionHandlerFactory exceptionHandlerFactory) {
        super(exceptionHandlerFactory);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Creates new {@link ElDocExceptionHandler}
     *
     * @return
     */
    @Override
    public ExceptionHandler getExceptionHandler() {
        return new ElDocExceptionHandler(getWrapped().getExceptionHandler());
    }

}
