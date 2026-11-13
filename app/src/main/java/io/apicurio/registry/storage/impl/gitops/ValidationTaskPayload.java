package io.apicurio.registry.storage.impl.gitops;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ValidationTaskPayload(
        @JsonProperty("type") String type,
        @JsonProperty("repoId") String repoId,
        @JsonProperty("ref") String ref,
        @JsonProperty("createdAt") long createdAt,
        @JsonProperty("nonce") String nonce) {

    public Instant createdAtInstant() {
        return Instant.ofEpochSecond(createdAt);
    }
}
