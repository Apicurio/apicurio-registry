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

package io.apicurio.registry.search.client;

import java.util.Properties;

/**
 * @author Ales Justin
 */
public class SearchUtil {

    /**
     * First we look into env vars, then properties,
     * if none is found, we return default value.
     *
     * @param properties the properties
     * @param key the key
     * @param defaultValue default value
     * @return property
     */
    public static String property(Properties properties, String key, String defaultValue) {
        String value = System.getenv(key.replace(".", "_").toUpperCase());
        if (value == null) {
            value = properties.getProperty(key);
        }
        if (value == null) {
            value = System.getProperty(key, defaultValue);
        }
        return value;
    }

}
