package io.apicurio.registry.references;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
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
 * change than #10634 is meant to be. It preserves two behaviours of the generic walker it replaces that are
 * themselves considered bugs, deliberately, rather than fixing them as a side effect here:
 * <ul>
 * <li>A reference that fails to load is logged and skipped, not failed — fixed by #9626, which also removes
 * this class once no caller still needs the old loader shape.</li>
 * <li>Two different targets sharing a reference name collapse to whichever the walk resolves first — fixed
 * properly by #9000, which keys on coordinates instead.</li>
 * </ul>
 * A {@link ForbiddenException} is the one loader failure that is never swallowed: the caller lacking
 * permission to read a referenced artifact is not the same as the reference not existing, and must not be
 * reported as if it were.
 */
public final class ReferenceResolution {

    private static final Logger log = LoggerFactory.getLogger(ReferenceResolution.class);

    /**
     * {@code apicurio.storage.references.max-depth} read directly via {@link ConfigProvider} rather than
     * injected, because this is a static utility called from about twenty places, not all of them CDI
     * beans with that property already available. See {@code HttpCompressionWriterInterceptor} and
     * {@code GitOpsConfig} for the same pattern elsewhere in this codebase. The value is re-read on every
     * call rather than cached, matching how {@code @ConfigProperty}-injected fields pick up config changes
     * only on redeploy anyway — this is not on a hot enough path for that to matter.
     */
    private static final String MAX_DEPTH_PROPERTY = "apicurio.storage.references.max-depth";

    private static final int DEFAULT_MAX_DEPTH = 100;

    private ReferenceResolution() {
    }

    /**
     * Resolves {@code references} to a name-to-content map, exactly as the generic walker this replaces
     * did: see {@link ResolvedReferences#asNameMap()} for how same-named collisions and ordering are
     * decided now. {@code loader} is called once per distinct target coordinate in the graph (not once per
     * reference, and not once per occurrence of a name), via the batching {@link ReferenceGraphWalker}.
     *
     * @param references the references to resolve; {@code null} or empty returns an empty map
     * @param loader loads one artifact version's content by its coordinates; a {@code null} return means
     *            "does not exist", recorded in the walk but not treated as an error
     * @return the name-to-content map every content type handler's reference-resolution contract expects
     */
    public static Map<String, TypedContent> recursivelyResolveReferences(List<ArtifactReferenceDto> references,
            Function<ArtifactReferenceDto, ContentWrapperDto> loader) {
        ReferenceLoader batchLoader = coordinates -> {
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV coordinates1 : coordinates) {
                ContentWrapperDto content = loadOne(coordinates1, loader);
                if (content != null) {
                    loaded.put(coordinates1,
                            ReferenceNode.builder().coordinates(coordinates1)
                                    // Content type, not artifact type: regression test for #10428.
                                    .content(TypedContent.create(content.getContent(), content.getContentType()))
                                    .artifactType(content.getArtifactType())
                                    .references(content.getReferences()).build());
                }
            }
            return loaded;
        };
        ResolvedReferences resolved = ReferenceGraphWalker.walk(references, batchLoader, maxDepth());
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
            for (GAV coordinates1 : coordinates) {
                StoredArtifactVersionDto version = loadOne(coordinates1, loader);
                if (version != null) {
                    loaded.put(coordinates1,
                            ReferenceNode.builder().coordinates(coordinates1)
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

    private static int maxDepth() {
        return ConfigProvider.getConfig().getOptionalValue(MAX_DEPTH_PROPERTY, Integer.class)
                .orElse(DEFAULT_MAX_DEPTH);
    }
}
