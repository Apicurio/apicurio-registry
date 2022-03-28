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

package io.apicurio.registry.rules.validity;


import java.util.Collections;
import org.everit.json.schema.SchemaException;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.compatibility.jsonschema.JsonUtil;
import io.apicurio.registry.types.RuleType;

import java.util.Map;

/**
 * A content validator implementation for the JsonSchema content type.
 * @author eric.wittmann@gmail.com
 */
public class JsonSchemaContentValidator implements ContentValidator {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructor.
     */
    public JsonSchemaContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, ContentHandle, Map)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY) {
            try {
                objectMapper.readTree(artifactContent.bytes());
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for JSON Schema artifact.", RuleType.VALIDITY, level.name(), e);
            }
        } else if (level == ValidityLevel.FULL) {
            try {
                JsonUtil.readSchema(artifactContent.content(), resolvedReferences);
            } catch (SchemaException e) {
                String context = e.getSchemaLocation();
                String description = e.getMessage();
                if (description != null && description.contains(":")) {
                    description = description.substring(description.indexOf(":") + 1).trim();
                }
                RuleViolation violation = new RuleViolation(description, context);
                throw new RuleViolationException("Syntax or semantic violation for JSON Schema artifact.", RuleType.VALIDITY, level.name(),
                        Collections.singleton(violation));
            } catch (Exception e) {
                RuleViolation violation = new RuleViolation("JSON schema not valid: " + e.getMessage(), "");
                throw new RuleViolationException("Syntax or semantic violation for JSON Schema artifact.", RuleType.VALIDITY, level.name(),
                        Collections.singleton(violation));
            }
        }
    }
}
