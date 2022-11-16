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

package io.apicurio.registry.util;

import io.apicurio.registry.AbstractRegistryTestBase;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.InvalidArtifactTypeException;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author eric.wittmann@gmail.com
 */
class ArtifactTypeUtilTest extends AbstractRegistryTestBase {

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_JSON() {
        ContentHandle content = resourceToContentHandle("json-schema.json");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("JSON", type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_Avro() {
        ContentHandle content = resourceToContentHandle("avro.json");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("AVRO", type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_Avro_Simple() {
        ContentHandle content = resourceToContentHandle("avro-simple.avsc");
        Schema s = new Schema.Parser().parse(content.content());
        assertEquals(Type.STRING, s.getType());

        String type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("AVRO", type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_Proto() {
        ContentHandle content = resourceToContentHandle("protobuf.proto");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("PROTOBUF", type);

        content = resourceToContentHandle("protobuf.proto");
        type = ArtifactTypeUtil.determineArtifactType(content, null, "application/x-protobuf");
        Assertions.assertEquals("PROTOBUF", type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_OpenApi() {
        ContentHandle content = resourceToContentHandle("openapi.json");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("OPENAPI", type);

        content = resourceToContentHandle("swagger.json");
        type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("OPENAPI", type);

        content = resourceToContentHandle("swagger.json");
        type = ArtifactTypeUtil.determineArtifactType(content, null, "application/json");
        Assertions.assertEquals("OPENAPI", type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_AsyncApi() {
        ContentHandle content = resourceToContentHandle("asyncapi.json");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("ASYNCAPI", type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_GraphQL() {
        ContentHandle content = resourceToContentHandle("example.graphql");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("GRAPHQL", type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_DefaultNotFound() {
        Assertions.assertThrows(InvalidArtifactTypeException.class, () -> {
            ContentHandle content = resourceToContentHandle("example.txt");
            ArtifactTypeUtil.determineArtifactType(content, null, null);
        });
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_Xml() {
        ContentHandle content = resourceToContentHandle("xml.xml");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("XML", type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_Xsd() {
        ContentHandle content = resourceToContentHandle("xml-schema.xsd");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("XSD", type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_Wsdl() {
        ContentHandle content = resourceToContentHandle("wsdl.wsdl");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("WSDL", type);

        content = resourceToContentHandle("wsdl-2.0.wsdl");
        type = ArtifactTypeUtil.determineArtifactType(content, null, null);
        Assertions.assertEquals("WSDL", type);
    }

}
