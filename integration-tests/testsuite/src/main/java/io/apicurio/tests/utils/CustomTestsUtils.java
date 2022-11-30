/*
 * Copyright 2021 Red Hat
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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.commons.codec.digest.DigestUtils;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;

/**
 * @author Fabian Martinez
 */
public class CustomTestsUtils {

    public static ArtifactData createArtifact(RegistryClient client, String type, String content) throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData meta = client.createArtifact(null, artifactId, type, IoUtil.toStream(content));
        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));
        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));
        String contentHash = DigestUtils.sha256Hex(IoUtil.toBytes(content));
        return new ArtifactData(meta, contentHash);
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
