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

package io.apicurio.registry.content.canon;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.apicurio.registry.content.ContentHandle;

/**
 * A common JSON content canonicalizer.  This will remove any extra formatting such as whitespace
 * and also sort all fields/properties for all objects (because ordering of properties does not
 * matter in JSON).
 * 
 * @author eric.wittmann@gmail.com
 */
public class JsonContentCanonicalizer implements ContentCanonicalizer {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    /**
     * @see ContentCanonicalizer#canonicalize(io.apicurio.registry.content.ContentHandle, Map)
     */
    @Override
    public ContentHandle canonicalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        try {
            JsonNode root = readAsJsonNode(content);
            processJsonNode(root);
            String converted = mapper.writeValueAsString(mapper.treeToValue(root, Object.class));
            return ContentHandle.create(converted);
        } catch (Throwable t) {
            return content;
        }
    }

    /**
     * Perform any additional processing on the JSON node.  The base JSON canonicalizer 
     * does nothing extra.
     * @param node
     */
    protected void processJsonNode(JsonNode node) {
    }

    /**
     * @param content
     * @return
     * @throws IOException
     */
    private JsonNode readAsJsonNode(ContentHandle content) throws IOException {
        return mapper.readTree(content.content());
    }

}
