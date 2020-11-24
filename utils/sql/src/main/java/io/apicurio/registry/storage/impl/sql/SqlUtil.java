/*
 * Copyright 2020 JBoss Inc
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

package io.apicurio.registry.storage.impl.sql;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.utils.StringUtil;

/**
 * @author eric.wittmann@gmail.com
 */
public class SqlUtil {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Serializes the given collection of labels to a string for storage in the DB.
     * @param labels
     */
    public static String serializeLabels(List<String> labels) {
        try {
            if (labels == null) {
                return null;
            }
            if (labels.isEmpty()) {
                return null;
            }
            return mapper.writeValueAsString(labels);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deserialize the labels from their string form to a List<String> form.
     * @param labelsStr
     */
    @SuppressWarnings("unchecked")
    public static List<String> deserializeLabels(String labelsStr) {
        try {
            if (StringUtil.isEmpty(labelsStr)) {
                return null;
            }
            return (List<String>) mapper.readValue(labelsStr, List.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Serializes the given collection of properties to a string for storage in the DB.
     * @param properties
     */
    public static String serializeProperties(Map<String, String> properties) {
        try {
            if (properties == null) {
                return null;
            }
            if (properties.isEmpty()) {
                return null;
            }
            return mapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deserialize the properties from their string form to a Map<String, String> form.
     * @param propertiesStr
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> deserializeProperties(String propertiesStr) {
        try {
            if (StringUtil.isEmpty(propertiesStr)) {
                return null;
            }
            return (Map<String, String>) mapper.readValue(propertiesStr, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    
    
    
}
