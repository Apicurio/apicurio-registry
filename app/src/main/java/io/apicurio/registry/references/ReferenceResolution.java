package io.apicurio.registry.references;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.quarkus.security.ForbiddenException;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Bridges the pre-#10634 reference-resolution API — a one-at-a-time {@code Function<ArtifactReferenceDto,
 * ?>} loader, called from about twenty places across REST, rules, polling and importing — onto
 * {@link ReferenceGraphWalker}, without changing any of those call sites' signatures.
 * <p>
 * This class exists only because rewriting every caller to build its own batch {@link ReferenceLoader} and
 * handle errors itself, on the day the walker was introduced, would have been a much larger and riskier
 * change than #10634 is meant to be. It preserves one behaviour of the generic walker it replaces that is
 * itself considered a bug, deliberately, rather than fixing it as a side effect here: a reference that fails
 * to load is logged and skipped, not failed — fixed by #9626, which also removes this class once no caller
 * still needs the old loader shape.
 * <p>
 * A {@link ForbiddenException} is the one loader failure that is never swallowed: the caller lacking
 * permission to read a referenced artifact is not the same as the reference not existing, and must not be
 * reported as if it were.
 */
public final class ReferenceResolution {

    private static final Logger log = LoggerFactory.getLogger(ReferenceResolution.class);

    private ReferenceResolution() {
    }

    /**
     * Resolves {@code references} to a name-to-content map — the same contract the walker this class
     * bridges to replaces, except without its #9000 bug: two different targets sharing a reference name no
     * longer collapse to whichever the walk resolves first.
     * <p>
     * All the actual work — the bottom-up, node-by-node dereferencing that avoids the collision, with its
     * worked example and the fallback for a type that cannot dereference — is
     * {@link ResolvedReferences#dereferenceAll}. This method only walks the graph and then narrows that
     * whole-graph result down to {@code references}' own names with
     * {@link ResolvedReferences#scopedReferences}: the same per-node operation
     * {@link ResolvedReferences#dereferenceAll} already uses for every other node, applied once more here
     * for the root, which (unlike every other node) is never itself part of the resolved graph.
     * <p>
     * <b>This result is root-scoped, not whole-graph-flattened</b> — a real behaviour difference from
     * {@link ResolvedReferences#asNameMap()}, not just a bug fix. {@code asNameMap()} returns one entry per
     * distinct name discovered <em>anywhere</em> in the whole graph, root and every descendant together.
     * This method returns only the names {@code references} (the root's own direct references) declares;
     * a name used solely by some descendant — {@code "common.avsc"} in the worked example — is consumed
     * while dereferencing that descendant and never appears as a top-level key here. That is intentional:
     * the whole point is that such a name must never reach a caller through a single shared map in the
     * first place.
     * <p>
     * The fix does not touch the {@code Map<String, TypedContent>} contract that
     * {@code ContentDereferencer}/{@code ContentCanonicalizer}/{@code ContentValidator} take. As
     * {@link ResolvedReferences#dereferenceAll} explains, that is deliberate for now, not a final answer:
     * dereferencing one node at a time from outside the content-type libraries is a workaround, not where
     * this should live long-term (tracked by #10761 and Apitomy/apitomy-data-models#1289).
     *
     * @param references the references to resolve; {@code null} or empty returns an empty map
     * @param loader loads one artifact version's content by its coordinates; a {@code null} return means
     *            "does not exist", recorded in the walk but not treated as an error
     * @param typeProviderFactory resolves the {@code ContentDereferencer} for each node's own artifact type
     * @return the name-to-content map every content type handler's reference-resolution contract expects,
     *         with every entry's content already dereferenced, scoped to {@code references}' own names
     */
    public static Map<String, TypedContent> recursivelyResolveReferences(List<ArtifactReferenceDto> references,
            Function<ArtifactReferenceDto, ContentWrapperDto> loader,
            ArtifactTypeUtilProviderFactory typeProviderFactory) {
        if (references == null || references.isEmpty()) {
            return Map.of();
        }
        ResolvedReferences resolved = walk(references, loader);
        Map<GAV, TypedContent> dereferencedByCoordinate = resolved.dereferenceAll(typeProviderFactory);
        return ResolvedReferences.scopedReferences(references, dereferencedByCoordinate);
    }

    private static ResolvedReferences walk(List<ArtifactReferenceDto> references,
            Function<ArtifactReferenceDto, ContentWrapperDto> loader) {
        ReferenceLoader batchLoader = coordinates -> {
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV coordinate : coordinates) {
                ContentWrapperDto content = loadOne(coordinate, loader);
                if (content != null) {
                    loaded.put(coordinate,
                            ReferenceNode.builder().coordinates(coordinate)
                                    // Content type, not artifact type: regression test for #10428.
                                    .content(TypedContent.create(content.getContent(), content.getContentType()))
                                    .artifactType(content.getArtifactType())
                                    .references(content.getReferences()).build());
                }
            }
            return loaded;
        };
        return ReferenceGraphWalker.walk(references, batchLoader, maxDepth());
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
                StoredArtifactVersionDto version = loadOne(coordinate, loader);
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
        return ReferenceGraphWalker.walk(rootReferences, batchLoader, maxDepth()).contentIds();
    }

    /**
     * Calls a one-at-a-time loader for one coordinate, logging and skipping (returning {@code null}, "does
     * not exist") anything the loader throws except {@link ForbiddenException}, which always propagates:
     * the caller being forbidden to read a reference is never the same as the reference not existing.
     */
    private static <T> T loadOne(GAV coordinates, Function<ArtifactReferenceDto, T> loader) {
        ArtifactReferenceDto reference = ArtifactReferenceDto.builder()
                .groupId(coordinates.getRawGroupIdWithNull()).artifactId(coordinates.getRawArtifactId())
                .version(coordinates.getRawVersionId()).build();
        try {
            return loader.apply(reference);
        } catch (ForbiddenException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Could not resolve reference {}.", coordinates, ex);
            return null;
        }
    }

    /**
     * Reads {@link ReferenceResolutionConfig#MAX_DEPTH_PROPERTY} directly via {@link ConfigProvider} rather
     * than injecting it, because this is a static utility called from about twenty places, not all of them
     * CDI beans with that property already available. See {@code HttpCompressionWriterInterceptor} and
     * {@code GitOpsConfig} for the same pattern elsewhere in this codebase. The value is re-read on every
     * call rather than cached, matching how {@code @ConfigProperty}-injected fields pick up config changes
     * only on redeploy anyway — this is not on a hot enough path for that to matter.
     */
    private static int maxDepth() {
        return ConfigProvider.getConfig()
                .getOptionalValue(ReferenceResolutionConfig.MAX_DEPTH_PROPERTY, Integer.class)
                .orElse(ReferenceResolutionConfig.DEFAULT_MAX_DEPTH);
    }
}
