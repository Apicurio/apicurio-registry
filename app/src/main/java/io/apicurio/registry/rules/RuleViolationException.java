/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.rules;

import io.apicurio.registry.types.RegistryException;

/**
 * Exception thrown when a configured rule is violated, rejecting an artifact content
 * update.
 * @author Ales Justin
 */
public class RuleViolationException extends RegistryException {
    
    private static final long serialVersionUID = 8437151164241883773L;

    /**
     * Constructor.
     * @param message
     */
    public RuleViolationException(String message) {
        super(message);
    }

    /**
     * Constructor.
     * @param message
     * @param cause
     */
    public RuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
