/*
 * Copyright 2023 Red Hat
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

package io.apicurio.tests.utils;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CustomTestsUtils {

    public static ArtifactData createArtifact(RegistryClient client, String type, String content) throws Exception {
        return createArtifact(client, null, type, content);
    }

    public static ArtifactData createArtifact(RegistryClient client, String group, String type, String content) throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData meta = client.createArtifact(group, artifactId, type, IoUtil.toStream(content));
        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));
        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));
        String contentHash = DigestUtils.sha256Hex(IoUtil.toBytes(content));
        return new ArtifactData(meta, contentHash);
    }

    public static ArtifactData createArtifactWithReferences(String groupId, String artifactId, RegistryClient client, String type, String content, List<ArtifactReference> references) throws Exception {
        ArtifactMetaData meta = client.createArtifact(groupId, artifactId, null, type, IfExists.RETURN, false, null, null, ContentTypes.APPLICATION_CREATE_EXTENDED, null, null, IoUtil.toStream(content), references);
        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));
        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));
        String contentHash = DigestUtils.sha256Hex(IoUtil.toBytes(content));
        return new ArtifactData(meta, contentHash);
    }

    public static ArtifactData createArtifactWithReferences(String artifactId, RegistryClient client, String type, String content, List<ArtifactReference> references) throws Exception {
        return createArtifactWithReferences(null, artifactId, client, type, content, references);
    }

    public static class ArtifactData {
        public ArtifactMetaData meta;
        public String contentHash;

        public ArtifactData(ArtifactMetaData meta, String contentHash) {
            this.meta = meta;
            this.contentHash = contentHash;
        }
    }
}