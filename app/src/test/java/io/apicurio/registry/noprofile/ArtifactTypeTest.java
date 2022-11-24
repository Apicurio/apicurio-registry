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

package io.apicurio.registry.noprofile;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.JsonSchemas;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;
import io.apicurio.registry.rules.compatibility.JsonSchemaCompatibilityDifference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class ArtifactTypeTest extends AbstractRegistryTestBase {

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    private static String PROTO_DATA = "syntax = \"proto2\";\n" +
            "\n" +
            "message ProtoSchema {\n" +
            "  required string message = 1;\n" +
            "  required int64 time = 2;\n" +
            "}";

    private static String PROTO_DATA_2 = "syntax = \"proto2\";\n" +
            "\n" +
            "message ProtoSchema {\n" +
            "  required string message = 1;\n" +
            "  required int64 time = 2;\n" +
            "  required string code = 3;\n" +
            "}";

    @Test
    public void testAvro() {
        String avroString = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}";
        String avro = ArtifactType.AVRO;
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(avro);
        CompatibilityChecker checker = provider.getCompatibilityChecker();

        CompatibilityExecutionResult compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.emptyList(), avroString);
        Assertions.assertTrue(compatibilityExecutionResult.isCompatible());
        Assertions.assertTrue(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());

        String avroString2 = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\", \"qq\":\"ff\"}]}";
        compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(avroString), avroString2);
        Assertions.assertTrue(compatibilityExecutionResult.isCompatible());
        Assertions.assertTrue(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());
    }

    @Test
    public void testJson() {
        String jsonString = JsonSchemas.jsonSchema;
        String incompatibleJsonString = JsonSchemas.incompatibleJsonSchema;
        String json = ArtifactType.JSON;
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(json);
        CompatibilityChecker checker = provider.getCompatibilityChecker();

        Assertions.assertTrue(checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.emptyList(), jsonString).isCompatible());
        Assertions.assertTrue(checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(jsonString), jsonString).isCompatible());

        CompatibilityExecutionResult compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(jsonString), incompatibleJsonString);
        Assertions.assertFalse(compatibilityExecutionResult.isCompatible());
        Set<CompatibilityDifference> incompatibleDifferences = compatibilityExecutionResult.getIncompatibleDifferences();
        Difference ageDiff = findDiffByPathUpdated(incompatibleDifferences, "/properties/age");
        Difference zipCodeDiff = findDiffByPathUpdated(incompatibleDifferences, "/properties/zipcode");
        Assertions.assertEquals(DiffType.SUBSCHEMA_TYPE_CHANGED.getDescription(), ageDiff.getDiffType().getDescription());
        Assertions.assertEquals("/properties/age", ageDiff.getPathUpdated());
        Assertions.assertEquals(DiffType.SUBSCHEMA_TYPE_CHANGED.getDescription(), zipCodeDiff.getDiffType().getDescription());
        Assertions.assertEquals("/properties/zipcode", zipCodeDiff.getPathUpdated());
    }

    private Difference findDiffByPathUpdated(Set<CompatibilityDifference> incompatibleDifferences, String path) {
        for(CompatibilityDifference cd : incompatibleDifferences) {
            JsonSchemaCompatibilityDifference jsonSchemaCompatibilityDifference = (JsonSchemaCompatibilityDifference) cd;
            Difference diff = jsonSchemaCompatibilityDifference.getDifference();
            if(diff.getPathUpdated().equals(path)) {
                return diff;
            }
        }
        return null;
    }

    @Test
    public void testProtobuf() {
        String data = "syntax = \"proto3\";\n" +
                      "package test;\n" +
                      "\n" +
                      "message Channel {\n" +
                      "  int64 id = 1;\n" +
                      "  string name = 2;\n" +
                      "  string description = 3;\n" +
                      "}\n" +
                      "\n" +
                      "message NextRequest {}\n" +
                      "message PreviousRequest {}\n" +
                      "\n" +
                      "service ChannelChanger {\n" +
                      "\trpc Next(stream NextRequest) returns (Channel);\n" +
                      "\trpc Previous(PreviousRequest) returns (stream Channel);\n" +
                      "}\n";

        String protobuf = ArtifactType.PROTOBUF;
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(protobuf);
        CompatibilityChecker checker = provider.getCompatibilityChecker();

        CompatibilityExecutionResult compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.emptyList(), data);
        Assertions.assertTrue(compatibilityExecutionResult.isCompatible());
        Assertions.assertTrue(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());

        String data2 = "syntax = \"proto3\";\n" +
                       "package test;\n" +
                       "\n" +
                       "message Channel {\n" +
                       "  int64 id = 1;\n" +
                       "  string name = 2;\n" +
                       //"  reserved 3;\n" +
                       //"  reserved \"description\";\n" +
                       "  string description = 3;\n" + // TODO
                       "  string newff = 4;\n" +
                       "}\n" +
                       "\n" +
                       "message NextRequest {}\n" +
                       "message PreviousRequest {}\n" +
                       "\n" +
                       "service ChannelChanger {\n" +
                       "\trpc Next(stream NextRequest) returns (Channel);\n" +
                       "\trpc Previous(PreviousRequest) returns (stream Channel);\n" +
                       "}\n";

        compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(data), data2);
        Assertions.assertTrue(compatibilityExecutionResult.isCompatible());
        Assertions.assertTrue(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());

        String data3 = "syntax = \"proto3\";\n" +
                       "package test;\n" +
                       "\n" +
                       "message Channel {\n" +
                       "  int64 id = 1;\n" +
                       "  string name = 2;\n" +
                       "  string description = 4;\n" +
                       "}\n" +
                       "\n" +
                       "message NextRequest {}\n" +
                       "message PreviousRequest {}\n" +
                       "\n" +
                       "service ChannelChanger {\n" +
                       "\trpc Next(stream NextRequest) returns (Channel);\n" +
                       "\trpc Previous(PreviousRequest) returns (stream Channel);\n" +
                       "}\n";

        compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(data), data3);
        Assertions.assertFalse(compatibilityExecutionResult.isCompatible());
        Assertions.assertFalse(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());
        Assertions.assertEquals("The new version of the protobuf artifact is not backward compatible.", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription());
        Assertions.assertEquals("/", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getContext());
    }

    @Test
    public void testProtobufV2() {
        String data = "syntax = \"proto2\";\n" +
                "\n" +
                "message ProtoSchema {\n" +
                "  required string message = 1;\n" +
                "  required int64 time = 2;\n" +
                "}";

        String protobuf = ArtifactType.PROTOBUF;
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(protobuf);
        CompatibilityChecker checker = provider.getCompatibilityChecker();

        CompatibilityExecutionResult compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.emptyList(), data);
        Assertions.assertTrue(compatibilityExecutionResult.isCompatible());
        Assertions.assertTrue(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());

        String data2 = "syntax = \"proto2\";\n" +
                "\n" +
                "message ProtoSchema {\n" +
                "  required string message = 1;\n" +
                "  required int64 time = 2;\n" +
                "  required string code = 3;\n" +
                "}";

        compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(data), data2);
        Assertions.assertFalse(compatibilityExecutionResult.isCompatible());
        Assertions.assertFalse(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());
        Assertions.assertEquals("The new version of the protobuf artifact is not backward compatible.", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription());
        Assertions.assertEquals("/", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getContext());
    }

    @Test
    public void testProtobufBackwardTransitive() {
        String protobuf = ArtifactType.PROTOBUF;
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(protobuf);
        CompatibilityChecker checker = provider.getCompatibilityChecker();

        //adding a required field is not allowed since the first schema does not have it, should fail
        CompatibilityExecutionResult compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.BACKWARD_TRANSITIVE, List.of(PROTO_DATA, PROTO_DATA_2), PROTO_DATA_2);
        Assertions.assertFalse(compatibilityExecutionResult.isCompatible());
        Assertions.assertFalse(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());
        Assertions.assertEquals("The new version of the protobuf artifact is not backward compatible.", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription());
        Assertions.assertEquals("/", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getContext());
    }


    @Test
    public void testProtobufForward() {
        String protobuf = ArtifactType.PROTOBUF;
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(protobuf);
        CompatibilityChecker checker = provider.getCompatibilityChecker();

        //adding a required field is not allowed, should fail
        CompatibilityExecutionResult compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.FORWARD, Collections.singletonList(PROTO_DATA_2), PROTO_DATA);
        Assertions.assertFalse(compatibilityExecutionResult.isCompatible());
        Assertions.assertFalse(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());
        Assertions.assertEquals("The new version of the protobuf artifact is not forward compatible.", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription());
        Assertions.assertEquals("/", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getContext());

        //adding a required field is allowed since we're only checking forward, not forward transitive
        compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.FORWARD, List.of(PROTO_DATA, PROTO_DATA_2), PROTO_DATA_2);
        Assertions.assertTrue(compatibilityExecutionResult.isCompatible());
        Assertions.assertTrue(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());
    }

    @Test
    public void testProtobufForwardTransitive() {
        String protobuf = ArtifactType.PROTOBUF;
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(protobuf);
        CompatibilityChecker checker = provider.getCompatibilityChecker();

        //must pass, all the existing schemas are the same
        CompatibilityExecutionResult compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.FORWARD_TRANSITIVE, List.of(PROTO_DATA_2, PROTO_DATA_2), PROTO_DATA_2);
        Assertions.assertTrue(compatibilityExecutionResult.isCompatible());
        Assertions.assertTrue(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());

        //adding a required field is not allowed since we're now checking forward transitive and the field is not present, not forward transitive
        compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.FORWARD_TRANSITIVE, List.of(PROTO_DATA, PROTO_DATA_2), PROTO_DATA_2);
        Assertions.assertFalse(compatibilityExecutionResult.isCompatible());
        Assertions.assertFalse(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());
        Assertions.assertEquals("The new version of the protobuf artifact is not forward compatible.", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription());
        Assertions.assertEquals("/", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getContext());
    }

    @Test
    public void testProtobufFull() {
        String protobuf = ArtifactType.PROTOBUF;
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(protobuf);
        CompatibilityChecker checker = provider.getCompatibilityChecker();

        //adding a required field is not allowed since we're now checking forward transitive and the field is not present, not forward transitive
        CompatibilityExecutionResult compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.FULL, List.of(PROTO_DATA), PROTO_DATA_2);
        Assertions.assertFalse(compatibilityExecutionResult.isCompatible());
        Assertions.assertFalse(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());
        Assertions.assertEquals("The new version of the protobuf artifact is not fully compatible.", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription());
        Assertions.assertEquals("/", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getContext());

        //must pass, since the schema is both backwards and forwards compatible with the latest existing schema
        compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.FULL, List.of(PROTO_DATA, PROTO_DATA_2), PROTO_DATA_2);
        Assertions.assertTrue(compatibilityExecutionResult.isCompatible());
        Assertions.assertTrue(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());

        //must fail, the schema is not compatible with the first existing schema
        compatibilityExecutionResult = checker.testCompatibility(CompatibilityLevel.FULL_TRANSITIVE, List.of(PROTO_DATA, PROTO_DATA_2), PROTO_DATA_2);
        Assertions.assertFalse(compatibilityExecutionResult.isCompatible());
        Assertions.assertFalse(compatibilityExecutionResult.getIncompatibleDifferences().isEmpty());
        Assertions.assertEquals("The new version of the protobuf artifact is not fully compatible.", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription());
        Assertions.assertEquals("/", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolation().getContext());
    }
}
