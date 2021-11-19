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

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;

/**
 * Performs meta-data extraction for JSON Schema content.
 * @author Ales Justin
 */
public class JsonContentExtractor implements ContentExtractor {

    Logger log = LoggerFactory.getLogger(getClass());

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode jsonSchema = mapper.readTree(content.bytes());
            JsonNode title = jsonSchema.get("title");
            JsonNode desc = jsonSchema.get("description");

            ExtractedMetaData metaData = null;
            if (title != null && !title.isNull()) {
                metaData = new ExtractedMetaData();
                metaData.setName(title.asText());
            }
            if (desc != null && !desc.isNull()) {
                if (metaData == null) {
                    metaData = new ExtractedMetaData();
                }
                metaData.setDescription(desc.asText());
            }
            return metaData;
        } catch (IOException e) {
            log.warn("Error extracting metadata from JSON: {}", e.getMessage());
            return null;
        }
    }
}
