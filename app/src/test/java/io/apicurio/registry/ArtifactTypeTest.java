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

package io.apicurio.registry;

import java.util.Collections;
import java.util.Set;

import javax.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;
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

    @Test
    public void testAvro() {
        String avroString = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}";
        ArtifactType avro = ArtifactType.AVRO;
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
        ArtifactType json = ArtifactType.JSON;
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
            Difference diff = (Difference) cd;
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

        ArtifactType protobuf = ArtifactType.PROTOBUF;
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
        Assertions.assertEquals("The new version of the protobuf artifact is not backward compatible.", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolationCause().getDescription());
        Assertions.assertEquals("/", compatibilityExecutionResult.getIncompatibleDifferences().iterator().next().asRuleViolationCause().getContext());
    }
}
