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

/**
 * @author eric.wittmann@gmail.com
 */
public class MissingRequiredParameterException extends RegistryException {

    private static final long serialVersionUID = 3318387244830092754L;
    
    private final String parameter;
    
    /**
     * Constructor.
     */
    public MissingRequiredParameterException(String parameter) {
        super("Request is missing a required parameter: " + parameter);
        this.parameter = parameter;
    }
    
    /**
     * @return the parameter
     */
    public String getParameter() {
        return parameter;
    }

}
