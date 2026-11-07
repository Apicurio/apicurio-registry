package io.apicurio.registry.references;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Bridges the pre-#10634 reference-resolution API — a one-at-a-time {@code Function<ArtifactReferenceDto,
 * ?>} loader, called from about twenty places across REST, rules, polling and importing — onto
 * {@link ReferenceGraphWalker}, without changing any of those call sites' signatures.
 * <p>
 * This class exists because rewriting every caller to build its own batch {@link ReferenceLoader} and
 * handle errors itself would have been a much larger and riskier change than #10634. It still preserves
 * one behaviour of the generic walker it replaces that is itself a known issue:
 * <ul>
 * <li>Two different targets sharing a reference name collapse to whichever the walk resolves first — fixed
 * properly by #9000, which keys on coordinates instead.</li>
 * </ul>
 * As of #9626, loader exceptions are no longer swallowed: any failure other than a missing reference
 * (a {@code null} return from the loader, which the walker records in {@link ResolvedReferences#missing()})
 * propagates to the caller immediately.
 */
public final class ReferenceResolution {

    private ReferenceResolution() {
    }

    /**
     * Resolves {@code references} to a name-to-content map, exactly as the generic walker this replaces
     * did: see {@link ResolvedReferences#asNameMap()} for how same-named collisions and ordering are
     * decided now. {@code loader} is called once per distinct target coordinate in the graph (not once per
     * reference, and not once per occurrence of a name), via the batching {@link ReferenceGraphWalker}. If it
     * is an {@link AuthorizingReferenceLoader}, each level of the graph is authorized at once, before any of
     * it is loaded.
     *
     * @param references the references to resolve; {@code null} or empty returns an empty map
     * @param loader loads one artifact version's content by its coordinates; a {@code null} return means
     *            "does not exist", recorded in the walk but not treated as an error
     * @return the name-to-content map every content type handler's reference-resolution contract expects
     */
    public static Map<String, TypedContent> recursivelyResolveReferences(List<ArtifactReferenceDto> references,
            Function<ArtifactReferenceDto, ContentWrapperDto> loader) {
        ReferenceLoader batchLoader = coordinates -> {
            AuthorizingReferenceLoader.authorizeAll(loader,
                    coordinates.stream().map(ReferenceResolution::toReference).toList());
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV coordinate : coordinates) {
                ContentWrapperDto content = loader.apply(toReference(coordinate));
                if (content != null) {
                    loaded.put(coordinate,
                            ReferenceNode.builder().coordinates(coordinate)
                                    .content(TypedContent.create(content.getContent(), content.getContentType()))
                                    .artifactType(content.getArtifactType())
                                    .references(content.getReferences()).build());
                }
            }
            return loaded;
        };
        ResolvedReferences resolved = ReferenceGraphWalker.walk(references, batchLoader,
                ReferenceResolutionConfig.maxDepth());
        return resolved.asNameMap();
    }

    /**
     * Collects the content IDs of every artifact version in {@code root}'s reference tree, distinct and
     * sorted in natural order. Used for identity views, such as the ETag over a whole reference tree, where
     * the set of content matters but names and order do not.
     *
     * @param root the artifact version whose reference tree is collected; not itself included in the result
     * @param loader loads one artifact version by its coordinates; a {@code null} return means "does not
     *            exist", recorded in the walk but not treated as an error
     * @return the distinct, sorted content IDs of every resolved node in the tree
     */
    public static List<Long> recursivelyResolveReferenceContentIds(StoredArtifactVersionDto root,
            Function<ArtifactReferenceDto, StoredArtifactVersionDto> loader) {
        ReferenceLoader batchLoader = coordinates -> {
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV coordinate : coordinates) {
                StoredArtifactVersionDto version = loader.apply(toReference(coordinate));
                if (version != null) {
                    loaded.put(coordinate,
                            ReferenceNode.builder().coordinates(coordinate)
                                    .content(TypedContent.create(version.getContent(), version.getContentType()))
                                    .contentId(version.getContentId()).references(version.getReferences())
                                    .build());
                }
            }
            return loaded;
        };
        List<ArtifactReferenceDto> rootReferences = root == null ? List.of() : root.getReferences();
        return ReferenceGraphWalker.walk(rootReferences, batchLoader, ReferenceResolutionConfig.maxDepth())
                .contentIds();
    }

    private static ArtifactReferenceDto toReference(GAV coordinates) {
        return ArtifactReferenceDto.builder().groupId(coordinates.getRawGroupIdWithNull())
                .artifactId(coordinates.getRawArtifactId()).version(coordinates.getRawVersionId()).build();
    }
}
