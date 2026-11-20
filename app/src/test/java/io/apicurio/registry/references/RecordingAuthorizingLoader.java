package io.apicurio.registry.references;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.security.ForbiddenException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An {@link AuthorizingReferenceLoader} over an in-memory reference graph, recording how it is called:
 * the batches passed to {@link #authorize} and the references loaded through {@link #apply}. Artifacts
 * named in {@code denied} are not readable. Artifacts are identified by artifact ID only.
 */
public class RecordingAuthorizingLoader implements AuthorizingReferenceLoader {

    private final Map<String, List<ArtifactReferenceDto>> graph = new HashMap<>();
    private final Set<String> denied;

    public final List<List<String>> authorizedBatches = new ArrayList<>();
    public final List<String> loaded = new ArrayList<>();

    public RecordingAuthorizingLoader(Set<String> denied) {
        this.denied = denied;
    }

    public static ArtifactReferenceDto ref(String artifactId) {
        return ArtifactReferenceDto.builder().groupId("g").artifactId(artifactId).version("1").name(artifactId)
                .build();
    }

    /** Declares that {@code artifactId} references {@code targets}. */
    public RecordingAuthorizingLoader with(String artifactId, String... targets) {
        graph.put(artifactId, List.of(targets).stream().map(RecordingAuthorizingLoader::ref).toList());
        return this;
    }

    @Override
    public void authorize(Collection<ArtifactReferenceDto> references) {
        List<String> batch = references.stream().map(ArtifactReferenceDto::getArtifactId).toList();
        authorizedBatches.add(batch);
        if (batch.stream().anyMatch(denied::contains)) {
            throw new ForbiddenException("denied");
        }
    }

    @Override
    public ContentWrapperDto apply(ArtifactReferenceDto reference) {
        loaded.add(reference.getArtifactId());
        return ContentWrapperDto.builder().content(ContentHandle.create("{}"))
                .contentType(ContentTypes.APPLICATION_JSON).artifactType(ArtifactType.JSON)
                .references(graph.getOrDefault(reference.getArtifactId(), List.of())).build();
    }
}
