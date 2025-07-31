package io.apicurio.registry.resolver.cache;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

/**
 * Used as a key in the ER cache. Content can't be used as a key alone,
 * because two artifact versions might differ only in the updated references.
 * <p>
 * We rely on equals and hashCode of @ArtifactReference being implemented correctly.
 */
@Builder
@Getter
@EqualsAndHashCode
@ToString
public class ContentWithReferences {

    @NonNull
    String content;

    @NonNull
    @Builder.Default // Avoid potential problems with equality where null and empty set are not equal.
    Set<ArtifactReference> references = new HashSet<>();
}
