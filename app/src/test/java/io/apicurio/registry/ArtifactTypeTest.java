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

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ArtifactTypeAdapter;
import io.apicurio.registry.types.ArtifactWrapper;
import io.apicurio.registry.types.CompatibilityLevel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;

/**
 * @author Ales Justin
 */
public class ArtifactTypeTest {

    @Test
    public void testSerialization() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(ArtifactType.protobuf);

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        ArtifactType at = (ArtifactType) ois.readObject();
        Assertions.assertNotNull(at.getAdapter());
    }

    @Test
    public void testAvro() {
        String avroString = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}";
        ArtifactType avro = ArtifactType.avro;
        ArtifactTypeAdapter adapter = avro.getAdapter();
        ArtifactWrapper avroWrapper = adapter.wrapper(avroString);

        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD.name(), Collections.emptyList(), avroString));
        String avroString2 = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\", \"qq\":\"ff\"}]}";
        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD.name(), Collections.singletonList(avroString), avroString2));
    }

    @Test
    public void testJson() {
        String jsonString = "{\"name\":\"foobar\"}";
        ArtifactType json = ArtifactType.json;
        ArtifactTypeAdapter adapter = json.getAdapter();
        ArtifactWrapper jsonWrapper = adapter.wrapper(jsonString);

        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD.name(), Collections.emptyList(), jsonString));
        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD.name(), Collections.singletonList(jsonString), jsonString));
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

        ArtifactType protobuf = ArtifactType.protobuf;
        ArtifactTypeAdapter adapter = protobuf.getAdapter();
        ArtifactWrapper protobufWrapper = adapter.wrapper(data);

        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD.name(), Collections.emptyList(), data));

        String data2 = "syntax = \"proto3\";\n" +
                       "package test;\n" +
                       "\n" +
                       "message Channel {\n" +
                       "  int64 id = 1;\n" +
                       "  string name = 2;\n" +
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

        Assertions.assertTrue(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD.name(), Collections.singletonList(data), data2));

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

        Assertions.assertFalse(adapter.isCompatibleWith(CompatibilityLevel.BACKWARD.name(), Collections.singletonList(data), data3));
    }
}
