package io.apicurio.registry.references;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.stream.Collectors;

/**
 * One artifact version visited while walking a reference graph, as loaded by a {@link ReferenceLoader}.
 * <p>
 * A node is immutable and carries everything a caller of {@link ReferenceGraphWalker} needs about the
 * artifact version it represents: its content, the artifact type that content is in, the content ID (so
 * that callers needing a content-identity view, such as an ETag, do not have to reload it), and the list of
 * outbound references this version's content itself contains — the edges the walker followed to discover
 * the next level of the graph.
 * <p>
 * {@code contentId} may be {@code null}: not every {@link ReferenceLoader} has a content ID readily
 * available (for example, one adapting an existing one-at-a-time loader that only returns content).
 * Callers that need it, such as the content-ID view used for ETags, must use a loader that supplies it.
 * <p>
 * {@link #getReferences()} returns an unmodifiable list, of copies of each {@link ArtifactReferenceDto}
 * rather than the loader's own instances, taken at construction time: a loader that mutates or reuses what
 * it handed to the builder — the list itself, or one of its elements, which {@link ArtifactReferenceDto}
 * leaves mutable via {@code @Setter} — must not be able to change a node already stored in a
 * {@link ResolvedReferences} out from under it. The list itself cannot be added to or removed from; the
 * copied elements, being plain mutable DTOs, are not further protected once obtained from the getter.
 */
@Builder
@Getter
@EqualsAndHashCode
@ToString
public final class ReferenceNode {

    /** The coordinates this node was loaded for. Matches the key it is stored under in {@link ResolvedReferences}. */
    private final GAV coordinates;

    private final TypedContent content;

    /** The artifact type of {@link #content}, for example {@code AVRO} or {@code JSON}. */
    private final String artifactType;

    /** The content ID of {@link #content}, or {@code null} if the loader does not supply one. */
    private final Long contentId;

    /**
     * The references this node's own content declares. The walker follows these to discover the next
     * level of the graph; each one is an edge, named by {@link ArtifactReferenceDto#getName()}, from this
     * node to the coordinates the reference resolves to.
     */
    private final List<ArtifactReferenceDto> references;

    public static class ReferenceNodeBuilder {

        public ReferenceNodeBuilder references(List<ArtifactReferenceDto> references) {
            this.references = references == null ? null
                    : references.stream().map(ReferenceNode::copyOf).collect(Collectors.toUnmodifiableList());
            return this;
        }
    }

    private static ArtifactReferenceDto copyOf(ArtifactReferenceDto reference) {
        return ArtifactReferenceDto.builder().groupId(reference.getGroupId())
                .artifactId(reference.getArtifactId()).version(reference.getVersion())
                .name(reference.getName()).build();
    }
}
