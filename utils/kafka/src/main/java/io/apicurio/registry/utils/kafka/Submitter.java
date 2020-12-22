/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

package io.apicurio.registry.utils.kafka;

import com.google.protobuf.ByteString;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * @author Ales Justin
 */
public class Submitter<T> {

    private static final String CREATED_BY = "createdBy";

    private Function<Str.StorageValue, CompletableFuture<T>> submitFn;

    public Submitter(Function<Str.StorageValue, CompletableFuture<T>> submitFn) {
        this.submitFn = submitFn;
    }

    private CompletableFuture<T> submit(Str.StorageValue value) {
        return submitFn.apply(value);
    }

    private Str.StorageValue.Builder getRVBuilder(Str.ValueType vt, Str.ActionType actionType, String artifactId, long version) {
        Str.StorageValue.Builder builder = Str.StorageValue.newBuilder()
                                                           .setVt(vt)
                                                           .setType(actionType)
                                                           .setVersion(version);

        if (artifactId != null) {
            builder.setArtifactId(artifactId);
        }
        return builder;
    }

    public CompletableFuture<T> submitArtifact(Str.ActionType actionType, String artifactId, long version, ArtifactType artifactType, byte[] content, String createdBy) {
        Str.ArtifactValue.Builder builder = Str.ArtifactValue.newBuilder();
        if (artifactType != null) {
            builder.setArtifactType(artifactType.ordinal());
        }
        if (content != null) {
            builder.setContent(ByteString.copyFrom(content));
        }

        if (createdBy != null) {
            builder.putMetadata(CREATED_BY, createdBy);
        }

        Str.StorageValue.Builder rvb = getRVBuilder(Str.ValueType.ARTIFACT, actionType, artifactId, version).setArtifact(builder);
        return submit(rvb.build());
    }

    public CompletableFuture<T> submitMetadata(Str.ActionType actionType, String artifactId, long version, String name, String description, List<String> labels, Map<String, String> properties) {
        Str.MetaDataValue.Builder builder = Str.MetaDataValue.newBuilder();
        if (name != null) {
            builder.setName(name);
        }
        if (description != null) {
            builder.setDescription(description);
        }

        if (labels != null && !labels.isEmpty()) {
            builder.setLabels(String.join(",", labels));
        }

        if (properties != null && !properties.isEmpty()) {
            builder.putAllProperties(properties);
        }

        Str.StorageValue.Builder rvb = getRVBuilder(Str.ValueType.METADATA, actionType, artifactId, version).setMetadata(builder);
        return submit(rvb.build());
    }

    public CompletableFuture<T> submitRule(Str.ActionType actionType, String artifactId, RuleType type, String configuration) {
        Str.RuleValue.Builder builder = Str.RuleValue.newBuilder();
        if (type != null) {
            builder.setType(Str.RuleType.valueOf(type.name()));
        }
        if (configuration != null) {
            builder.setConfiguration(configuration);
        }

        Str.StorageValue.Builder rvb = getRVBuilder(Str.ValueType.RULE, actionType, artifactId, -1L).setRule(builder);
        return submit(rvb.build());
    }

    public CompletableFuture<T> submitSnapshot(long timestamp) {
        Str.SnapshotValue.Builder builder = Str.SnapshotValue.newBuilder().setTimestamp(timestamp);
        Str.StorageValue.Builder rvb = getRVBuilder(Str.ValueType.SNAPSHOT, Str.ActionType.CREATE, null, -1).setSnapshot(builder);
        return submit(rvb.build());
    }

    public CompletableFuture<T> submitState(String artifactId, Long version, ArtifactState state) {
        Str.StorageValue.Builder rvb = getRVBuilder(Str.ValueType.STATE,
                                                    Str.ActionType.UPDATE,
                                                    artifactId, version != null ? version : -1L)
            .setState(Str.ArtifactState.valueOf(state.name()));
        return submit(rvb.build());
    }

}
