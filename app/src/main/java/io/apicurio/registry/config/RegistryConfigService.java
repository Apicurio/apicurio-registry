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

package io.apicurio.registry.config;

import java.util.Optional;

/**
 * A configuration service capable of dynamically fetching config property values.  This service
 * is capable of getting config property values from the database (first) and from `application.properties`
 * (fallback).
 *
 * If no value is found, returns the default value provided.
 *
 * @author eric.wittmann@gmail.com
 */
public interface RegistryConfigService {

    public String get(RegistryConfigProperty property);
    public <T> T get(RegistryConfigProperty property, Class<T> propertyType);
    public Optional<String> getOptional(RegistryConfigProperty property);
    public <T> Optional<T> getOptional(RegistryConfigProperty property, Class<T> propertyType);

}
