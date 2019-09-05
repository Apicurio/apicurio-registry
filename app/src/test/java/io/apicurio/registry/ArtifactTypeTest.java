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

package io.apicurio.registry;

import io.apicurio.registry.rules.compatibility.ArtifactTypeAdapter;
import io.apicurio.registry.rules.compatibility.ArtifactTypeAdapterFactory;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * @author Ales Justin
 */
public class ArtifactTypeTest { // no need to extend TestBase

    @Test
    public void testAvro() {
        String avroString = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}";
        ArtifactType avro = ArtifactType.AVRO;
        ArtifactTypeAdapter adapter = ArtifactTypeAdapterFactory.toAdapter(avro);

        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD, Collections.emptyList(), avroString));
        String avroString2 = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\", \"qq\":\"ff\"}]}";
        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD, Collections.singletonList(avroString), avroString2));
    }

    @Test
    public void testJson() {
        String jsonString = "{\"name\":\"foobar\"}";
        ArtifactType json = ArtifactType.JSON;
        ArtifactTypeAdapter adapter = ArtifactTypeAdapterFactory.toAdapter(json);

        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD, Collections.emptyList(), jsonString));
        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD, Collections.singletonList(jsonString), jsonString));
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

        ArtifactType protobuf = ArtifactType.PROTOBUFF;
        ArtifactTypeAdapter adapter = ArtifactTypeAdapterFactory.toAdapter(protobuf);

        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD, Collections.emptyList(), data));

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

        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD, Collections.singletonList(data), data2));

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

        Assertions.assertFalse(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD, Collections.singletonList(data), data3));
    }
}
