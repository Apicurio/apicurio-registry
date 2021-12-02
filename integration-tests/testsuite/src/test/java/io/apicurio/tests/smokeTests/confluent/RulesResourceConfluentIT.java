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

package io.apicurio.tests.smokeTests.confluent;

import static io.apicurio.tests.common.Constants.SMOKE;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ConfluentBaseIT;
import io.apicurio.tests.common.utils.subUtils.ConfluentConfigUtils;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;

@Tag(SMOKE)
public class RulesResourceConfluentIT extends ConfluentBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataConfluentIT.class);

    @Test
    @Tag(ACCEPTANCE)
    void compatibilityGlobalRules() throws Exception {
        ConfluentConfigUtils.createGlobalCompatibilityConfig("FULL");


        ParsedSchema schema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");

        String schemeSubject = TestUtils.generateArtifactId();
        int schemaId = createArtifactViaConfluentClient(schema, schemeSubject);

        confluentService.getSchemaById(schemaId);

        ParsedSchema newSchema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");

        createArtifactViaConfluentClient(newSchema, schemeSubject);

        LOGGER.info("Checking 'Compability with same scheme' and expected code {}", 200);
        ConfluentConfigUtils.testCompatibility("{\"schema\":\"{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"myrecord2\\\",\\\"fields\\\":[{\\\"name\\\":\\\"foo\\\",\\\"type\\\":\\\"string\\\"}]}\"}", schemeSubject, 200);

        LOGGER.info("Checking 'Subject not found' and expected code {}", 404);
        ConfluentConfigUtils.testCompatibility("{\"schema\":\"{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"myrecord2\\\",\\\"fields\\\":[{\\\"name\\\":\\\"foo\\\",\\\"type\\\":\\\"string\\\"}]}\"}", "subject-not-found", 404);

        LOGGER.info("Checking 'Invalid avro format' and expected code {}", 422);
        ConfluentConfigUtils.testCompatibility("{\"schema\":\"{\\\"type\\\": \\\"bloop\\\"}\"}", schemeSubject, 422);

        confluentService.deleteSubject(schemeSubject);
        waitForSubjectDeleted(schemeSubject);
    }

    @AfterAll
    void clearRules() throws Exception {
        LOGGER.info("Removing all global rules");
        registryClient.deleteAllGlobalRules();
        retryOp((rc) -> {
            List<RuleType> rules = rc.listGlobalRules();
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}
