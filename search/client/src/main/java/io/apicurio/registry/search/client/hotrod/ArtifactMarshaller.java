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

package io.apicurio.registry.search.client.hotrod;

import io.apicurio.registry.common.proto.Cmmn;
import io.apicurio.registry.search.client.common.InfinispanSearchClient;
import io.apicurio.registry.search.common.Search;
import io.apicurio.registry.utils.ProtoUtil;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;

/**
 * @author Ales Justin
 */
@SuppressWarnings("deprecation")
public class ArtifactMarshaller implements MessageMarshaller<Search.Artifact> {
    @Override
    public Search.Artifact readFrom(ProtoStreamReader reader) throws IOException {
        Search.Artifact.Builder builder = Search.Artifact
            .newBuilder()
            .setArtifactId(reader.readString("artifactId"))
            .setType(reader.readEnum("type", Cmmn.ArtifactType.class))
            .setContent(reader.readString("content"))
            .setVersion(reader.readLong("version"))
            .setGlobalId(reader.readLong("globalId"))
            .setName(ProtoUtil.emptyAsNull(reader.readString("name")))
            .setDescription(ProtoUtil.emptyAsNull(reader.readString("description")))
            .setCreatedBy(ProtoUtil.emptyAsNull(reader.readString("createdBy")));
        return builder.build();
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, Search.Artifact artifact) throws IOException {
        writer.writeString("artifactId", artifact.getArtifactId());
        writer.writeEnum("type", artifact.getType());
        writer.writeString("content", artifact.getContent());
        writer.writeLong("version", artifact.getVersion());
        writer.writeLong("globalId", artifact.getGlobalId());
        if (!ProtoUtil.isEmpty(artifact.getName())) {
            writer.writeString("name", artifact.getName());
        }
        if (!ProtoUtil.isEmpty(artifact.getName())) {
            writer.writeString("description", artifact.getDescription());
        }
        if (!ProtoUtil.isEmpty(artifact.getName())) {
            writer.writeString("createdBy", artifact.getCreatedBy());
        }
    }

    @Override
    public Class<? extends Search.Artifact> getJavaClass() {
        return Search.Artifact.class;
    }

    @Override
    public String getTypeName() {
        return InfinispanSearchClient.toFqn();
    }
}
