/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

/**
 * This exception covers the following errors in the compat API:
 * - Error code 42201 – Invalid schema
 * - Error code 42202 – Invalid schema version
 * - Error code 42203 – Invalid compatibility level
 */
public class UnprocessableEntityException extends RegistryException {

    private static final long serialVersionUID = 1791019542026597523L;

    public UnprocessableEntityException(String message) {
        super(message);
    }

    public UnprocessableEntityException(String message, Throwable cause) {
        super(message, cause);
    }
}
