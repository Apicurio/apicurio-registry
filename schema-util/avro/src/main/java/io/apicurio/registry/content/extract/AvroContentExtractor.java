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
 * Performs meta-data extraction for Avro content.
 * @author Ales Justin
 */
public class AvroContentExtractor implements ContentExtractor {

    Logger log = LoggerFactory.getLogger(getClass());

    private ObjectMapper mapper = new ObjectMapper();

    public AvroContentExtractor() {
    }

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode avroSchema = mapper.readTree(content.bytes());
            JsonNode name = avroSchema.get("name");

            ExtractedMetaData metaData = null;
            if (name != null && !name.isNull()) {
                metaData = new ExtractedMetaData();
                metaData.setName(name.asText());
            }
            return metaData;
        } catch (IOException e) {
            log.warn("Error extracting metadata from JSON: {}", e.getMessage());
            return null;
        }
    }
}
