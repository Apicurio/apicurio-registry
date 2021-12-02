/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.rest;

import io.apicurio.registry.types.RegistryException;

import java.util.Arrays;

public class ParametersConflictException extends RegistryException {

    private static final long serialVersionUID = 247427865185425744L;

    private final String[] parameters;

    public ParametersConflictException(String parameter1, String parameter2) {
        super("Conflict: '" + parameter1 + "' and '" + parameter2 + "' are mutually exclusive.");
        this.parameters = new String[] {parameter1, parameter2};
    }

    public ParametersConflictException(String... parameters) {
        super("Conflict: [" + String.join(",", parameters) + "] are mutually exclusive.");
        this.parameters = Arrays.stream(parameters).toArray(String[]::new);
    }

    public String[] getParameters() {
        return parameters;
    }
}
