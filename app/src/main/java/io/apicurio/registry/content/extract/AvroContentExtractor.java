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

package io.apicurio.registry.content.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.beans.EditableMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Performs meta-data extraction for Avro content.
 * @author Ales Justin
 */
public class AvroContentExtractor implements ContentExtractor {
    private static final Logger log = LoggerFactory.getLogger(AvroContentExtractor.class);

    public static final ContentExtractor INSTANCE = new AvroContentExtractor();

    private ObjectMapper mapper = new ObjectMapper();

    private AvroContentExtractor() {
    }

    public EditableMetaData extract(ContentHandle content) {
        try {
            JsonNode avroSchema = mapper.readTree(content.bytes());
            JsonNode name = avroSchema.get("name");
            
            EditableMetaData metaData = null;
            if (name != null && !name.isNull()) {
                metaData = new EditableMetaData();
                metaData.setName(name.asText());
            }
            return metaData;
        } catch (IOException e) {
            log.warn("Error extracting metadata from JSON: {}", e.getMessage());
            return null;
        }
    }
}
