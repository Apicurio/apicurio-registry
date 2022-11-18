/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.noprofile.validity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.exception.RuleViolationException;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class ValidityRuleApplicationTest extends AbstractResourceTestBase {

    private static final String SCHEMA_SIMPLE = "{\"type\": \"string\"}";
    private static final String INVALID_SCHEMA = "{\"type\": \"string";

    private static final String SCHEMA_WITH_MAP = "{\r\n" +
            "    \"type\": \"record\",\r\n" +
            "    \"name\": \"userInfo\",\r\n" +
            "    \"namespace\": \"my.example\",\r\n" +
            "    \"fields\": [\r\n" +
            "        {\r\n" +
            "            \"name\": \"name\",\r\n" +
            "            \"type\": \"string\",\r\n" +
            "            \"default\": \"NONE\"\r\n" +
            "        },\r\n" +
            "        {\r\n" +
            "            \"name\": \"props\",\r\n" +
            "            \"type\": {\r\n" +
            "                \"type\": \"map\",\r\n" +
            "                \"values\": \"string\"\r\n" +
            "            }\r\n" +
            "        }\r\n" +
            "    ]\r\n" +
            "}";
    private static final String INVALID_SCHEMA_WITH_MAP = "{\r\n" +
            "    \"type\": \"record\",\r\n" +
            "    \"name\": \"userInfo\",\r\n" +
            "    \"namespace\": \"my.example\",\r\n" +
            "    \"fields\": [\r\n" +
            "        {\r\n" +
            "            \"name\": \"name\",\r\n" +
            "            \"type\": \"string\",\r\n" +
            "            \"default\": \"NONE\"\r\n" +
            "        },\r\n" +
            "        {\r\n" +
            "            \"name\": \"props\",\r\n" +
            "            \"type\": {\r\n" +
            "                \"type\": \"map\",\r\n" +
            "                \"values\": \"string\"\r\n" +
            "            },\r\n" +
            "            \"default\": \"{}\"\r\n" +
            "        }\r\n" +
            "    ]\r\n" +
            "}";

    @Test
    public void testValidityRuleApplication() throws Exception {
        String artifactId = "ValidityRuleApplicationTest";
        createArtifact(artifactId, ArtifactType.AVRO, SCHEMA_SIMPLE);
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig(ValidityLevel.FULL.name());
        clientV2.createArtifactRule("default", artifactId, rule);

        Assertions.assertThrows(RuleViolationException.class, () -> {
            clientV2.updateArtifact("default", artifactId, IoUtil.toStream(INVALID_SCHEMA));
        });
    }

    @Test
    public void testValidityRuleApplication_Map() throws Exception {
        String artifactId = "testValidityRuleApplication_Map";
        createArtifact(artifactId, ArtifactType.AVRO, SCHEMA_WITH_MAP);
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig(ValidityLevel.FULL.name());
        clientV2.createArtifactRule("default", artifactId, rule);

        Assertions.assertThrows(RuleViolationException.class, () -> {
            clientV2.updateArtifact("default", artifactId, IoUtil.toStream(INVALID_SCHEMA_WITH_MAP));
        });
    }

}
