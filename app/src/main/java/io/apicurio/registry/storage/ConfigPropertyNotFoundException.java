/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.storage;

/**
 * @author eric.wittmann@gmail.com
 */
public class ConfigPropertyNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -9088094366735526214L;

    private String propertyName;

    /**
     * Constructor.
     * @param propertyName
     */
    public ConfigPropertyNotFoundException(String propertyName) {
        this.setPropertyName(propertyName);
    }

    /**
     * Constructor.
     * @param propertyName
     * @param cause
     */
    public ConfigPropertyNotFoundException(String propertyName, Throwable cause) {
        super(cause);
        this.setPropertyName(propertyName);
    }

    /**
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return "No configuration property named '" + this.propertyName + "' was found.";
    }

    /**
     * @return the propertyName
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * @param propertyName the propertyName to set
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

}