/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.types.ContentTypes;

/**
 * Utility class for canonicalizing JSON and YAML content.
 * Provides common functionality for artifact types that support both formats.
 */
public class JsonYamlCanonicalizer {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private JsonYamlCanonicalizer() {
    }

    /**
     * Canonicalizes content that can be in either JSON or YAML format.
     * The canonical form is always returned as sorted JSON.
     *
     * @param content the content to canonicalize
     * @return the canonicalized content as JSON
     * @throws ContentCanonicalizationException if canonicalization fails
     */
    public static TypedContent canonicalize(TypedContent content) throws ContentCanonicalizationException {
        try {
            JsonNode jsonNode = ContentTypeUtil.parseJsonOrYaml(content);
            String canonical = JSON_MAPPER.writeValueAsString(JSON_MAPPER.treeToValue(jsonNode, Object.class));
            return TypedContent.create(ContentHandle.create(canonical), ContentTypes.APPLICATION_JSON);
        } catch (Exception e) {
            throw new ContentCanonicalizationException("Failed to canonicalize content", e);
        }
    }
}
