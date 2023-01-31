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

package io.apicurio.registry.noprofile.compatibility;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.JsonSchemas;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.client.exception.UnprocessableSchemaException;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.compatibility.CompatibilityRuleExecutor;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@QuarkusTest
public class CompatibilityRuleApplicationTest extends AbstractResourceTestBase {

    private static final String SCHEMA_SIMPLE = "{\"type\": \"string\"}";
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
    private static final String SCHEMA_WITH_MAP_FIELD_REMOVED = "{\r\n" +
            "    \"type\": \"record\",\r\n" +
            "    \"name\": \"userInfo\",\r\n" +
            "    \"namespace\": \"my.example\",\r\n" +
            "    \"fields\": [\r\n" +
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

    @Inject
    RulesService rules;

    @Inject
    CompatibilityRuleExecutor compatibility;

    @Test
    public void testGlobalCompatibilityRuleNoArtifact() throws Exception {
        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("FULL");
        given()
                .when()
                .contentType(CT_JSON).body(rule)
                .post("/registry/v1/rules")
                .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added.
        TestUtils.retry(() -> {
            given()
                    .when()
                    .get("/registry/v1/rules/COMPATIBILITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("FULL"));
        });

        rules.applyRules("no-group", "not-existent", ArtifactType.AVRO, ContentHandle.create(SCHEMA_SIMPLE),
                RuleApplicationType.CREATE, Collections.emptyMap());
    }

    @Test
    public void testAvroCompatibility() {
        String v1Schema = "{\"type\":\"record\",\"namespace\":\"com.example\",\"name\":\"FullName\",\"fields\":[{\"name\":\"first\",\"type\":\"string\"},{\"name\":\"last\",\"type\":\"string\"}]}";
        String v2Schema = "{\"type\": \"string\"}";

        Assertions.assertThrows(RuleViolationException.class, () -> {
            RuleContext context = new RuleContext("TestGroup", "Test", "AVRO", "BACKWARD", Collections.singletonList(ContentHandle.create(v1Schema)), ContentHandle.create(v2Schema), Collections.emptyMap());
            compatibility.execute(context);
        });
    }

    @Test
    public void testJsonSchemaCompatibility() {
        String v1Schema = JsonSchemas.jsonSchema;
        String v2Schema = JsonSchemas.incompatibleJsonSchema;

        RuleViolationException ruleViolationException = Assertions.assertThrows(RuleViolationException.class, () -> {
            RuleContext context = new RuleContext("TestGroup", "TestJson", ArtifactType.JSON, "FORWARD_TRANSITIVE", Collections.singletonList(ContentHandle.create(v1Schema)), ContentHandle.create(v2Schema), Collections.emptyMap());
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

    @Test
    public void testCompatibilityRuleApplication_Map() throws Exception {
        String artifactId = "testCompatibilityRuleApplication_Map";
        createArtifact(artifactId, ArtifactType.AVRO, SCHEMA_WITH_MAP);
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig(CompatibilityLevel.FULL.name());
        clientV2.createArtifactRule("default", artifactId, rule);

        // Note: this should result in a rule violation exception due to a parse error in INVALID_SCHEMA_WITH_MAP
        // It turns out that "{}" is not a valid default for a map field.  This will throw a AvroTypeException from
        // the Avro parser.  It should be caught by the compatibility rule implementation and handled as a rule
        // violation (even though it's more of a validity failure).  The point is it should NOT result in a 500
        // error (which is reserved for server failures, not invalid content.
        Assertions.assertThrows(UnprocessableSchemaException.class, () -> {
            clientV2.updateArtifact("default", artifactId, IoUtil.toStream(INVALID_SCHEMA_WITH_MAP));
        });
    }

    @Test
    public void testCompatibilityInvalidExitingContentRuleApplication_Map() throws Exception {
        String artifactId = "testCompatibilityInvalidExitingContentRuleApplication_Map";
        createArtifact(artifactId, ArtifactType.AVRO, INVALID_SCHEMA_WITH_MAP);
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig(CompatibilityLevel.FULL.name());
        clientV2.createArtifactRule("default", artifactId, rule);

        // Note: this should result in a rule violation exception due to a parse error in INVALID_SCHEMA_WITH_MAP
        // It turns out that "{}" is not a valid default for a map field.  This will throw a AvroTypeException from
        // the Avro parser.  Since an invalid schema has been already created, this will fail earlier in the parsing
        // and should not result in a 500 error returned by the server.
        Assertions.assertThrows(UnprocessableSchemaException.class, () -> {
            clientV2.updateArtifact("default", artifactId, IoUtil.toStream(INVALID_SCHEMA_WITH_MAP));
        });
    }


    @Test
    public void testCompatibilityRuleApplication_FullTransitive() throws Exception {
        String artifactId = "testCompatibilityRuleApplication_FullTransitive";

        //Create artifact with 4 versions, where the first one is not compatible with the others
        createArtifact(artifactId, ArtifactType.AVRO, SCHEMA_SIMPLE);
        createArtifactVersion(artifactId, ArtifactType.AVRO, SCHEMA_WITH_MAP);
        createArtifactVersion(artifactId, ArtifactType.AVRO, SCHEMA_WITH_MAP);
        createArtifactVersion(artifactId, ArtifactType.AVRO, SCHEMA_WITH_MAP);
        createArtifactVersion(artifactId, ArtifactType.AVRO, SCHEMA_WITH_MAP);

        //Activate compatibility rules
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig(CompatibilityLevel.BACKWARD_TRANSITIVE.name());
        clientV2.createArtifactRule("default", artifactId, rule);

        //Should fail, the new version is not compatible with the first one
        Assertions.assertThrows(io.apicurio.registry.rest.client.exception.RuleViolationException.class, () -> {
            clientV2.updateArtifact("default", artifactId, IoUtil.toStream(SCHEMA_WITH_MAP));
        });

        //Change rule to backward, should pass since the new version is compatible with the latest one
        rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig(CompatibilityLevel.BACKWARD.name());
        clientV2.updateArtifactRuleConfig("default", artifactId, RuleType.COMPATIBILITY, rule);
        clientV2.updateArtifact("default", artifactId, IoUtil.toStream(SCHEMA_WITH_MAP));
    }
}
