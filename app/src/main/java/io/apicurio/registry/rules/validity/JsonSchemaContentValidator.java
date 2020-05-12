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

package io.apicurio.registry.rules.validity;

import javax.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import io.apicurio.registry.content.ContentHandle;

/**
 * A content validator implementation for the JsonSchema content type.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class JsonSchemaContentValidator implements ContentValidator {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Constructor.
     */
    public JsonSchemaContentValidator() {
    }
    
    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(io.apicurio.registry.rules.validity.ValidityLevel, ContentHandle)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent) throws InvalidContentException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                JsonNode node = objectMapper.readTree(artifactContent.bytes());
                if (level == ValidityLevel.FULL) {
                    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
                    factory.getSchema(node);
                }
            } catch (Exception e) {
                throw new InvalidContentException("Syntax violation for JSON Schema artifact.", e);
            }
        }
    }

}
