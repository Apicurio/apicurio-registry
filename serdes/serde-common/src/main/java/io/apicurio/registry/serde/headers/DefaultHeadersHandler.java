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

import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.config.IdOption;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author Fabian Martinez
 */
public class DefaultHeadersHandler implements HeadersHandler {

    private String globalIdHeaderName;
    private String contentIdHeaderName;
    private String groupIdHeaderName;
    private String artifactIdHeaderName;
    private String versionHeaderName;
    private IdOption idOption;

    /**
     * @see io.apicurio.registry.serde.headers.HeadersHandler#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, Object> configs, boolean isKey) {
        DefaultHeadersHandlerConfig config = new DefaultHeadersHandlerConfig(configs);
        if (isKey) {
            globalIdHeaderName = config.getKeyGlobalIdHeader();
            contentIdHeaderName = config.getKeyContentIdHeader();
            groupIdHeaderName = config.getKeyGroupIdHeader();
            artifactIdHeaderName = config.getKeyArtifactIdHeader();
            versionHeaderName = config.getKeyVersionHeader();
        } else {
            globalIdHeaderName = config.getValueGlobalIdHeader();
            contentIdHeaderName = config.getValueContentIdHeader();
            groupIdHeaderName = config.getValueGroupIdHeader();
            artifactIdHeaderName = config.getValueArtifactIdHeader();
            versionHeaderName = config.getValueVersionHeader();
        }
        idOption = config.useIdOption();
    }

    /**
     * @see io.apicurio.registry.serde.headers.HeadersHandler#writeHeaders(org.apache.kafka.common.header.Headers, io.apicurio.registry.serde.SchemaLookupResult)
     */
    @Override
    public void writeHeaders(Headers headers, ArtifactReference reference) {
        if (idOption == IdOption.contentId) {
            if (reference.getContentId() == null) {
                throw new SerializationException("Missing contentId. IdOption is contentId but there is no contentId in the ArtifactReference");
            }
            ByteBuffer buff = ByteBuffer.allocate(8);
            buff.putLong(reference.getContentId());
            headers.add(contentIdHeaderName, buff.array());
            return;
        }

        if (reference.getGlobalId() != null) {
            ByteBuffer buff = ByteBuffer.allocate(8);
            buff.putLong(reference.getGlobalId());
            headers.add(globalIdHeaderName, buff.array());
        } else {
            headers.add(groupIdHeaderName, IoUtil.toBytes(reference.getGroupId()));
            headers.add(artifactIdHeaderName, IoUtil.toBytes(reference.getArtifactId()));
            if (reference.getVersion() != null) {
                headers.add(versionHeaderName, IoUtil.toBytes(reference.getVersion()));
            }
        }
    }

    /**
     * @see io.apicurio.registry.serde.headers.HeadersHandler#readHeaders(org.apache.kafka.common.header.Headers)
     */
    @Override
    public ArtifactReference readHeaders(Headers headers) {
        return ArtifactReference.builder()
                .globalId(getGlobalId(headers))
                .contentId(getContentId(headers))
                .groupId(getGroupId(headers))
                .artifactId(getArtifactId(headers))
                .version(getVersion(headers))
                .build();
    }

    private String getGroupId(Headers headers) {
        Header header = headers.lastHeader(groupIdHeaderName);
        if (header == null) {
            return null;
        }
        return IoUtil.toString(header.value());
    }

    private String getArtifactId(Headers headers) {
        Header header = headers.lastHeader(artifactIdHeaderName);
        if (header == null) {
            return null;
        }
        return IoUtil.toString(header.value());
    }

    private String getVersion(Headers headers) {
        Header header = headers.lastHeader(versionHeaderName);
        if (header == null) {
            return null;
        }
        return IoUtil.toString(header.value());
    }

    private Long getGlobalId(Headers headers) {
        Header header = headers.lastHeader(globalIdHeaderName);
        if (header == null) {
            return null;
        } else {
            return ByteBuffer.wrap(header.value()).getLong();
        }
    }

    private Long getContentId(Headers headers) {
        Header header = headers.lastHeader(contentIdHeaderName);
        if (header == null) {
            return null;
        } else {
            return ByteBuffer.wrap(header.value()).getLong();
        }
    }

}
