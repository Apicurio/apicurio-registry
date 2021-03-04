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

package io.apicurio.registry.compatibility;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.JsonSchemas;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.rules.compatibility.CompatibilityRuleExecutor;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Set;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@QuarkusTest
public class CompatibilityRuleApplicationTest extends AbstractResourceTestBase {

    private static final String SCHEMA_SIMPLE = "{\"type\": \"string\"}";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesService rules;

    @Inject
    CompatibilityRuleExecutor compatibility;

    @Test
    public void testGlobalCompatibilityRuleNoArtifact() throws Exception {
        // this should NOT throw an exception
        storage.createGlobalRule(RuleType.COMPATIBILITY, RuleConfigurationDto.builder().configuration("FULL").build());
        TestUtils.retry(() -> {
            Assertions.assertTrue(storage.getGlobalRule(RuleType.COMPATIBILITY) != null);
        });
        rules.applyRules("no-group", "not-existent", ArtifactType.AVRO, ContentHandle.create(SCHEMA_SIMPLE),
            RuleApplicationType.CREATE);
    }

    @Test
    public void testAvroCompatibility() {
        String v1Schema = "{\"type\":\"record\",\"namespace\":\"com.example\",\"name\":\"FullName\",\"fields\":[{\"name\":\"first\",\"type\":\"string\"},{\"name\":\"last\",\"type\":\"string\"}]}";
        String v2Schema = "{\"type\": \"string\"}";

        Assertions.assertThrows(RuleViolationException.class, () -> {
            RuleContext context = new RuleContext("TestGroup", "Test", ArtifactType.AVRO, "BACKWARD", ContentHandle.create(v1Schema), ContentHandle.create(v2Schema));
            compatibility.execute(context);
        });
    }

    @Test
    public void testJsonSchemaCompatibility() {
        String v1Schema = JsonSchemas.jsonSchema;
        String v2Schema = JsonSchemas.incompatibleJsonSchema;

        RuleViolationException ruleViolationException = Assertions.assertThrows(RuleViolationException.class, () -> {
            RuleContext context = new RuleContext("TestGroup", "TestJson", ArtifactType.JSON, "FORWARD_TRANSITIVE", ContentHandle.create(v1Schema), ContentHandle.create(v2Schema));
            compatibility.execute(context);
        });

        Set<RuleViolation> ruleViolationCauses = ruleViolationException.getCauses();
        RuleViolation ageViolationCause = findCauseByContext(ruleViolationCauses, "/properties/age/type");
        RuleViolation zipCodeViolationCause = findCauseByContext(ruleViolationCauses, "/properties/zipcode");

        /* Explanation for why the following diff type is not SUBSCHEMA_TYPE_CHANGED:
         *
         * Consider the following schemas, with FORWARD compatibility checking
         * (i.e. B is newer, but is checked in a reverse order):
         * A:
         * ```
         * {
         *   "type": "object",
         *   "properties": {
         *     "age": {
         *       "type": "integer",
         *       "minimum": 0
         *     }
         *   }
         * }
         * ```
         * B:
         * ```
         * {
         *   "type": "object",
         *   "properties": {
         *     "age": {
         *       "type": "string",
         *       "minimum": 0
         *     }
         *   }
         * }
         * ```
         * A is incompatible with B, because the `type` property has been changed from `string` to `integer`,
         * however the `minimum` property, which is found in number schemas remained in B.
         * The Everit library parses subschema of the `age` property in B not as a string schema with an extra property,
         * but as a "synthetic" allOf combined schema of string and number.
         * The compatibility checking then compares this synthetic number subschema to the number schema in A.
         */
        Assertions.assertEquals("/properties/age/type", ageViolationCause.getContext());
        Assertions.assertEquals(DiffType.NUMBER_TYPE_INTEGER_REQUIRED_FALSE_TO_TRUE.getDescription(), ageViolationCause.getDescription());
        Assertions.assertEquals("/properties/zipcode", zipCodeViolationCause.getContext());
        Assertions.assertEquals(DiffType.SUBSCHEMA_TYPE_CHANGED.getDescription(), zipCodeViolationCause.getDescription());

    }

    private RuleViolation findCauseByContext(Set<RuleViolation> ruleViolations, String context) {
        for (RuleViolation violation : ruleViolations) {
            if (violation.getContext().equals(context)) {
                return violation;
            }
        }
        return null;
    }
}
