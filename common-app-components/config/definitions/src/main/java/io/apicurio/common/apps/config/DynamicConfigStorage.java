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

package io.apicurio.common.apps.config;

import java.util.List;

/**
 * @author eric.wittmann@gmail.com
 */
public interface DynamicConfigStorage {

    boolean isReady();

    /**
     * Should return the stored config property or null if not found.
     *
     * @param propertyName the name of a property
     * @return the property value or null if not found
     */
    public DynamicConfigPropertyDto getConfigProperty(String propertyName);

    /**
     * Sets a new value for a config property.
     *
     * @param propertyDto the property name and value
     */
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto);

    /**
     * Deletes a config property from storage.
     *
     * @param propertyName the name of a property
     */
    public void deleteConfigProperty(String propertyName);

    /**
     * Gets a list of all stored config properties.
     *
     * @return a list of stored properties
     */
    public List<DynamicConfigPropertyDto> getConfigProperties();

}
