package ch.cyberduck.core.b2;

/*
 * Copyright (c) 2002-2016 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.core.AbstractExceptionMappingService;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.InteroperabilityException;
import ch.cyberduck.core.exception.LoginFailureException;
import ch.cyberduck.core.exception.NotfoundException;
import ch.cyberduck.core.exception.QuotaException;

import org.apache.http.HttpStatus;

import synapticloop.b2.exception.B2Exception;

public class B2ExceptionMappingService extends AbstractExceptionMappingService<B2Exception> {
    @Override
    public BackgroundException map(final B2Exception e) {
        final StringBuilder buffer = new StringBuilder();
        this.append(buffer, e.getMessage());
        switch(e.getStatus()) {
            case HttpStatus.SC_UNAUTHORIZED:
                return new LoginFailureException(buffer.toString(), e);
            case HttpStatus.SC_FORBIDDEN:
                switch(e.getMessage()) {
                    case "cap_exceeded":
                        // Reached the storage cap that you set
                        return new QuotaException(buffer.toString(), e);
                }
                return new AccessDeniedException(buffer.toString(), e);
            case HttpStatus.SC_NOT_FOUND:
                return new NotfoundException(buffer.toString(), e);
            case HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE:
                return new QuotaException(buffer.toString(), e);
            case HttpStatus.SC_INSUFFICIENT_STORAGE:
                return new QuotaException(buffer.toString(), e);
            case HttpStatus.SC_PAYMENT_REQUIRED:
                return new QuotaException(buffer.toString(), e);
            case HttpStatus.SC_BAD_REQUEST:
                switch(e.getMessage()) {
                    case "cap_exceeded":
                        // Reached the storage cap that you set
                        return new QuotaException(buffer.toString(), e);
                }
                return new InteroperabilityException(buffer.toString(), e);
            case HttpStatus.SC_METHOD_NOT_ALLOWED:
                return new InteroperabilityException(buffer.toString(), e);
            case HttpStatus.SC_NOT_IMPLEMENTED:
                return new InteroperabilityException(buffer.toString(), e);
            case HttpStatus.SC_INTERNAL_SERVER_ERROR:
                return new InteroperabilityException(buffer.toString(), e);
        }
        return new BackgroundException(buffer.toString(), e);
    }
}
