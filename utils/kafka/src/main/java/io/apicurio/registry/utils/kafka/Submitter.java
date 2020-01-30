package io.apicurio.registry.utils.kafka;

import com.google.protobuf.ByteString;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * @author Ales Justin
 */
public class Submitter {
    private Function<Str.StorageValue, CompletableFuture<?>> submitFn;

    public Submitter(Function<Str.StorageValue, CompletableFuture<?>> submitFn) {
        this.submitFn = submitFn;
    }

    private <T> CompletableFuture<T> submit(Str.StorageValue value) {
        //noinspection unchecked
        return (CompletableFuture<T>) submitFn.apply(value);
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

    public <T> CompletableFuture<T> submitArtifact(Str.ActionType actionType, String artifactId, long version, ArtifactType artifactType, byte[] content) {
        Str.ArtifactValue.Builder builder = Str.ArtifactValue.newBuilder();
        if (artifactType != null) {
            builder.setArtifactType(artifactType.ordinal());
        }
        if (content != null) {
            builder.setContent(ByteString.copyFrom(content));
        }

        Str.StorageValue.Builder rvb = getRVBuilder(Str.ValueType.ARTIFACT, actionType, artifactId, version).setArtifact(builder);
        return submit(rvb.build());
    }

    public <T> CompletableFuture<T> submitMetadata(Str.ActionType actionType, String artifactId, long version, String name, String description) {
        Str.MetaDataValue.Builder builder = Str.MetaDataValue.newBuilder();
        if (name != null) {
            builder.setName(name);
        }
        if (description != null) {
            builder.setDescription(description);
        }

        Str.StorageValue.Builder rvb = getRVBuilder(Str.ValueType.METADATA, actionType, artifactId, version).setMetadata(builder);
        return submit(rvb.build());
    }

    public <T> CompletableFuture<T> submitRule(Str.ActionType actionType, String artifactId, RuleType type, String configuration) {
        Str.RuleValue.Builder builder = Str.RuleValue.newBuilder();
        if (type != null) {
            builder.setType(Str.RuleType.valueOf(type.name()));
        }
        if (configuration != null) {
            builder.setConfiguration(configuration);
        }

        Str.StorageValue.Builder rvb = getRVBuilder(Str.ValueType.RULE, actionType, artifactId, -1).setRule(builder);
        return submit(rvb.build());
    }

    public <T> CompletableFuture<T> submitSnapshot(long timestamp) {
        Str.SnapshotValue.Builder builder = Str.SnapshotValue.newBuilder().setTimestamp(timestamp);
        Str.StorageValue.Builder rvb = getRVBuilder(Str.ValueType.SNAPSHOT, Str.ActionType.CREATE, null, -1).setSnapshot(builder);
        return submit(rvb.build());
    }

    public <T> CompletableFuture<T> submitState(String artifactId, Long version, ArtifactState state) {
        Str.StorageValue.Builder rvb = getRVBuilder(Str.ValueType.STATE,
                                                    Str.ActionType.UPDATE,
                                                    artifactId, version != null ? version : -1L)
            .setState(Str.ArtifactState.valueOf(state.name()));
        return submit(rvb.build());
    }

}
