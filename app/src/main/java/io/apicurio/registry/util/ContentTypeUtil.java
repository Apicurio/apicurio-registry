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

package io.apicurio.registry.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.ContentHandle;

/**
 * @author eric.wittmann@gmail.com
 */
public final class ContentTypeUtil {
    
    public static final String CT_APPLICATION_JSON = "application/json";
    public static final String CT_APPLICATION_CREATE_EXTENDED = "application/create.extended+json";
    public static final String CT_APPLICATION_YAML = "application/x-yaml";
    public static final String CT_APPLICATION_XML = "application/xml";

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Returns true if the Content-Type of the inbound request is "application/json".
     *
     * @param ct content type
     */
    public static boolean isApplicationJson(String ct) {
        if (ct == null) {
            return false;
        }
        return ct.contains(CT_APPLICATION_JSON);
    }

    /**
     * Returns true if the Content-Type of the inbound request is "application/x-yaml".
     *
     * @param ct content type
     */
    public static boolean isApplicationYaml(String ct) {
        if (ct == null) {
            return false;
        }
        return ct.contains(CT_APPLICATION_YAML);
    }

    /**
     * Returns true if the Content-Type of the inbound request is "application/create.extended+json".
     *
     * @param ct content type
     */
    public static boolean isApplicationCreateExtended(String ct) {
        if (ct == null) {
            return false;
        }
        return ct.contains(CT_APPLICATION_CREATE_EXTENDED);
    }

    /**
     * Returns true if the content can be parsed as yaml.
     *
     */
    public static boolean isParsableYaml(ContentHandle yaml) {
        try {
            String content = yaml.content().trim();
            // it's Json or Xml
            if (content.startsWith("{") || content.startsWith("<")) {
                return false;
            }
            JsonNode root = yamlMapper.readTree(yaml.stream());
            return root != null && root.elements().hasNext();
        } catch (Throwable t) {
            return false;
        }
    }

    public static ContentHandle yamlToJson(ContentHandle yaml) {
        try {
            JsonNode root = yamlMapper.readTree(yaml.stream());
            return ContentHandle.create(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        } catch (Throwable t) {
            return yaml;
        }
    }

}
