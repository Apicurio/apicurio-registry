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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;

import java.util.Map;

/**
 * A YAML content canonicalizer. Sorts all object keys and outputs as canonical YAML.
 * This is useful for content types like prompt templates that are naturally expressed in YAML
 * and benefit from preserving multiline string formatting.
 */
public class YamlContentCanonicalizer extends BaseContentCanonicalizer {

    private static final ObjectMapper jsonMapper = new ObjectMapper()
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final ObjectMapper yamlMapper = new ObjectMapper(
        new YAMLFactory()
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Override
    protected TypedContent doCanonicalize(TypedContent content,
        Map<String, TypedContent> refs) throws ContentCanonicalizationException {
        try {
            JsonNode root = readAsJsonNode(content);
            Object ordered = jsonMapper.treeToValue(root, Object.class);
            String converted = yamlMapper.writeValueAsString(ordered);
            return TypedContent.create(ContentHandle.create(converted), ContentTypes.APPLICATION_YAML);
        } catch (Exception e) {
            throw new ContentCanonicalizationException("Failed to canonicalize YAML content", e);
        }
    }

    private JsonNode readAsJsonNode(TypedContent content) throws Exception {
        String raw = content.getContent().content();
        String ct = content.getContentType();
        if (ct != null && (ct.contains("yaml") || ct.contains("yml")
            || ct.equalsIgnoreCase("text/x-prompt-template"))) {
            return new ObjectMapper(new YAMLFactory()).readTree(raw);
        }
        return jsonMapper.readTree(raw);
    }
}
