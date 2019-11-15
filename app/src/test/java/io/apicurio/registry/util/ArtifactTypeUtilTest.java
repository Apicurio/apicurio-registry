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

package io.apicurio.registry.util;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.content.ContentHandle;
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
        ArtifactType type = ArtifactTypeUtil.discoverType(content, null);
        Assertions.assertEquals(ArtifactType.JSON, type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_Avro() {
        ContentHandle content = resourceToContentHandle("avro.json");
        ArtifactType type = ArtifactTypeUtil.discoverType(content, null);
        Assertions.assertEquals(ArtifactType.AVRO, type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_Proto() {
        ContentHandle content = resourceToContentHandle("protobuf.proto");
        ArtifactType type = ArtifactTypeUtil.discoverType(content, null);
        Assertions.assertEquals(ArtifactType.PROTOBUF, type);

        content = resourceToContentHandle("protobuf.proto");
        type = ArtifactTypeUtil.discoverType(content, "application/x-protobuf");
        Assertions.assertEquals(ArtifactType.PROTOBUF, type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_OpenApi() {
        ContentHandle content = resourceToContentHandle("openapi.json");
        ArtifactType type = ArtifactTypeUtil.discoverType(content, null);
        Assertions.assertEquals(ArtifactType.OPENAPI, type);

        content = resourceToContentHandle("swagger.json");
        type = ArtifactTypeUtil.discoverType(content, null);
        Assertions.assertEquals(ArtifactType.OPENAPI, type);

        content = resourceToContentHandle("swagger.json");
        type = ArtifactTypeUtil.discoverType(content, "application/json");
        Assertions.assertEquals(ArtifactType.OPENAPI, type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#discoverType(ContentHandle, java.lang.String)}.
     */
    @Test
    void testDiscoverType_AsyncApi() {
        ContentHandle content = resourceToContentHandle("asyncapi.json");
        ArtifactType type = ArtifactTypeUtil.discoverType(content, null);
        Assertions.assertEquals(ArtifactType.ASYNCAPI, type);
    }

}
