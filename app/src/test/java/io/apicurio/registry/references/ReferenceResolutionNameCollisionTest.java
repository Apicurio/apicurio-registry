package io.apicurio.registry.references;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Regression test for #9000: two different targets anywhere in the same reference graph sharing a
 * reference name must not collide. {@code A} and {@code B} each reference a different version of
 * {@code Common} under the identical reference name {@code "common.avsc"} — the exact shape of #9000's
 * reported bug, where the second occurrence used to silently overwrite the first in one flattened map.
 */
public class ReferenceResolutionNameCollisionTest {

    private static final ArtifactTypeUtilProviderFactory FACTORY = new DefaultArtifactTypeUtilProviderImpl(true);

    private static final String COMMON_V1 = "{\"type\":\"record\",\"name\":\"Common\",\"namespace\":\"test\","
            + "\"fields\":[{\"name\":\"v\",\"type\":\"int\"}]}";
    private static final String COMMON_V2 = "{\"type\":\"record\",\"name\":\"Common\",\"namespace\":\"test\","
            + "\"fields\":[{\"name\":\"v\",\"type\":\"string\"}]}";
    private static final String A_SCHEMA = "{\"type\":\"record\",\"name\":\"A\",\"namespace\":\"test\","
            + "\"fields\":[{\"name\":\"c\",\"type\":\"test.Common\"}]}";
    private static final String B_SCHEMA = "{\"type\":\"record\",\"name\":\"B\",\"namespace\":\"test\","
            + "\"fields\":[{\"name\":\"c\",\"type\":\"test.Common\"}]}";

    private static ArtifactReferenceDto reference(String artifactId, String version, String name) {
        return ArtifactReferenceDto.builder().groupId("default").artifactId(artifactId).version(version)
                .name(name).build();
    }

    private static ContentWrapperDto wrapper(String content, List<ArtifactReferenceDto> references) {
        return ContentWrapperDto.builder().content(ContentHandle.create(content))
                .contentType(ContentTypes.APPLICATION_JSON).artifactType(ArtifactType.AVRO)
                .references(references).build();
    }

    private static Function<ArtifactReferenceDto, ContentWrapperDto> loader() {
        return ref -> {
            if (ref.getArtifactId().equals("Common") && "1".equals(ref.getVersion())) {
                return wrapper(COMMON_V1, List.of());
            }
            if (ref.getArtifactId().equals("Common") && "2".equals(ref.getVersion())) {
                return wrapper(COMMON_V2, List.of());
            }
            if (ref.getArtifactId().equals("A")) {
                return wrapper(A_SCHEMA, List.of(reference("Common", "1", "common.avsc")));
            }
            if (ref.getArtifactId().equals("B")) {
                return wrapper(B_SCHEMA, List.of(reference("Common", "2", "common.avsc")));
            }
            return null;
        };
    }

    /**
     * The three-argument overload (the #9000 fix): processing the graph bottom-up means {@code A} and
     * {@code B} are each dereferenced using only their own direct reference named {@code "common.avsc"},
     * so one never clobbers the other even though the name is identical in both.
     */
    @Test
    void doesNotCollapseDifferentTargetsSharingAReferenceName() {
        List<ArtifactReferenceDto> mainReferences = List.of(reference("A", "1", "a.avsc"),
                reference("B", "1", "b.avsc"));

        Map<String, TypedContent> resolved = ReferenceResolution.recursivelyResolveReferences(mainReferences,
                loader(), FACTORY);

        Assertions.assertEquals(2, resolved.size());
        String aDereferenced = resolved.get("a.avsc").getContent().content();
        String bDereferenced = resolved.get("b.avsc").getContent().content();

        // A's own dependency, Common@1, declares field "v" as an int.
        Assertions.assertTrue(aDereferenced.contains("\"int\""),
                "Expected A's dereferenced content to inline Common@1's int field, got: " + aDereferenced);
        // B's own dependency, Common@2, declares field "v" as a string — must not have been overwritten
        // by Common@1 just because both A and B used the same reference name "common.avsc".
        Assertions.assertTrue(bDereferenced.contains("\"string\""),
                "Expected B's dereferenced content to inline Common@2's string field, got: " + bDereferenced);
    }

    /**
     * {@link ResolvedReferences#asNameMap()} itself still collapses a genuine collision by design (its own
     * Javadoc explains why: it is a flattened, whole-graph view by definition, so it cannot be node-aware
     * the way {@link ReferenceResolution}'s bottom-up orchestration is). No in-tree caller builds its
     * reference map this way any more — {@link ReferenceResolution#recursivelyResolveReferences} never
     * calls {@code asNameMap()} — but the method stays public, so this pins its documented behaviour
     * directly rather than through a caller.
     */
    @Test
    void asNameMapStillCollapsesByNameWhenCalledDirectly() {
        List<ArtifactReferenceDto> mainReferences = List.of(reference("A", "1", "a.avsc"),
                reference("B", "1", "b.avsc"));

        ReferenceLoader batchLoader = coordinates -> {
            Map<io.apicurio.registry.model.GAV, ReferenceNode> loaded = new java.util.LinkedHashMap<>();
            for (var coordinate : coordinates) {
                ContentWrapperDto content = loader().apply(ArtifactReferenceDto.builder()
                        .groupId(coordinate.getRawGroupIdWithNull()).artifactId(coordinate.getRawArtifactId())
                        .version(coordinate.getRawVersionId()).build());
                if (content != null) {
                    loaded.put(coordinate,
                            ReferenceNode.builder().coordinates(coordinate)
                                    .content(TypedContent.create(content.getContent(), content.getContentType()))
                                    .artifactType(content.getArtifactType()).references(content.getReferences())
                                    .build());
                }
            }
            return loaded;
        };
        Map<String, TypedContent> resolved = ReferenceGraphWalker.walk(mainReferences, batchLoader, 100)
                .asNameMap();

        // A, B and "common.avsc" are all present — but "common.avsc" collapsed to just one winner
        // (A's Common@1, discovered first), silently dropping B's Common@2 from the map entirely.
        Assertions.assertEquals(3, resolved.size());
        Assertions.assertTrue(resolved.containsKey("a.avsc"));
        Assertions.assertTrue(resolved.containsKey("b.avsc"));
        Assertions.assertTrue(resolved.get("common.avsc").getContent().content().contains("\"int\""),
                "Expected asNameMap() to keep only Common@1 (int), losing Common@2 (string) entirely — "
                        + "that is the #9000 bug this flattened view still has by design.");
    }
}
