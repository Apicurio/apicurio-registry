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

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.json.JsonConverterConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;

/**
 * A content validator implementation for the Kafka Connect schema content type.
 * @author eric.wittmann@gmail.com
 */
public class KafkaConnectContentValidator implements ContentValidator {

    private static final ObjectMapper mapper;
    private static final JsonConverter jsonConverter;
    static {
        mapper = new ObjectMapper();
        jsonConverter = new JsonConverter();
        Map<String, Object> configs = new HashMap<>();
        configs.put("converter.type", "key");
        configs.put(JsonConverterConfig.SCHEMAS_CACHE_SIZE_CONFIG, 0);
        jsonConverter.configure(configs);
    }

    /**
     * Constructor.
     */
    public KafkaConnectContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, ContentHandle, Map)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                JsonNode jsonNode = mapper.readTree(artifactContent.content());
                jsonConverter.asConnectSchema(jsonNode);
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for Kafka Connect Schema artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }

}
