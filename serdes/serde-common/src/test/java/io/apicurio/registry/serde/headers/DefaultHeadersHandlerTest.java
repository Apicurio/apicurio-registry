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
package io.apicurio.registry.serde.headers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.utils.IoUtil;

public class DefaultHeadersHandlerTest {

    @Test
    void testReadKeyHeadersHandlesPresentContentHash() {
        String contentHashHeaderName = "some key header name";
        String contentHashValue = "context hash value";
        Map<String, Object> configs = Collections.singletonMap(SerdeConfig.HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[]{new RecordHeader(contentHashHeaderName, IoUtil.toBytes(contentHashValue))});
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, true);
        
        ArtifactReference artifact = handler.readHeaders(headers);

        assertEquals(contentHashValue, artifact.getContentHash());
    }

    @Test
    void testReadKeyHeadersHandlesMissingContentHash() {
        String contentHashHeaderName = "another key header name";
        Map<String, Object> configs = Collections.singletonMap(SerdeConfig.HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[]{});
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, true);
        
        ArtifactReference artifact = handler.readHeaders(headers);

        assertEquals(null, artifact.getContentHash());
    }

    @Test
    void testReadValueHeadersHandlesPresentContentHash() {
        String contentHashHeaderName = "value header name";
        String contentHashValue = "some value";
        Map<String, Object> configs = Collections.singletonMap(SerdeConfig.HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[]{new RecordHeader(contentHashHeaderName, IoUtil.toBytes(contentHashValue))});
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);
        
        ArtifactReference artifact = handler.readHeaders(headers);

        assertEquals(contentHashValue, artifact.getContentHash());
    }

    @Test
    void testReadValueHeadersHandlesMissingContentHash() {
        String contentHashHeaderName = "another value header name";
        Map<String, Object> configs = Collections.singletonMap(SerdeConfig.HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[]{});
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);
        
        ArtifactReference artifact = handler.readHeaders(headers);

        assertEquals(null, artifact.getContentHash());
    }

    @Test
    void testWriteKeyHeadersHandlesPresentContentHash() {
        String contentHashHeaderName = "write key header name";
        String contentHashValue = "some write key value";
        Map<String, Object> configs = Collections.singletonMap(SerdeConfig.HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, true);
        ArtifactReference artifact = ArtifactReference.builder().contentHash(contentHashValue).build();

        handler.writeHeaders(headers, artifact);

        assertEquals(contentHashValue, IoUtil.toString(headers.lastHeader(contentHashHeaderName).value()));
    }

    @Test
    void testWriteKeyHeadersHandlesMissingContentHash() {
        String contentHashHeaderName = "another header name";
        Map<String, Object> configs = Collections.singletonMap(SerdeConfig.HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, true);
        ArtifactReference artifact = ArtifactReference.builder().build();

        handler.writeHeaders(headers, artifact);

        assertEquals(null, headers.lastHeader(contentHashHeaderName));
    }

    @Test
    void testWriteValueHeadersHandlesPresentContentHash() {
        String contentHashHeaderName = "write value header name";
        String contentHashValue = "some write value";
        Map<String, Object> configs = Collections.singletonMap(SerdeConfig.HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);
        ArtifactReference artifact = ArtifactReference.builder().contentHash(contentHashValue).build();

        handler.writeHeaders(headers, artifact);

        assertEquals(contentHashValue, IoUtil.toString(headers.lastHeader(contentHashHeaderName).value()));
    }

    @Test
    void testWriteValueHeadersHandlesMissingContentHash() {
        String contentHashHeaderName = "another write key header name";
        Map<String, Object> configs = Collections.singletonMap(SerdeConfig.HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);
        ArtifactReference artifact = ArtifactReference.builder().build();

        handler.writeHeaders(headers, artifact);

        assertEquals(null, headers.lastHeader(contentHashHeaderName));
    }

    @Test
    void testWritesArtifactCoordinatesWhenContentHashPresent() {
        String contentHashHeaderName = "content hash";
        String artifactIdHeaderName = "artifact ID";
        String contentHashValue = "a content hash";
        String artifactIdValue = "a artifact ID";

        Map<String, Object> configs = new HashMap<>();
        configs.put(SerdeConfig.HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        configs.put(SerdeConfig.HEADER_VALUE_ARTIFACT_ID_OVERRIDE_NAME, artifactIdHeaderName);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);
        ArtifactReference artifact = ArtifactReference.builder()
            .contentHash(contentHashValue)
            .artifactId(artifactIdValue)
            .build();

        handler.writeHeaders(headers, artifact);

        assertNotNull(headers.lastHeader(contentHashHeaderName));
        assertEquals(contentHashValue, IoUtil.toString(headers.lastHeader(contentHashHeaderName).value()));
        assertNotNull(headers.lastHeader(artifactIdHeaderName));
        assertEquals(artifactIdValue, IoUtil.toString(headers.lastHeader(artifactIdHeaderName).value()));
    }
}
