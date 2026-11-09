package io.apicurio.registry.references.auto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.resolve.SchemaSourceSet;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.types.ArtifactType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Matches external references found in content to existing artifacts in the registry.
 */
@ApplicationScoped
public class ReferenceMatcher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    ArtifactLookup lookup;

    ReferenceMatcher() {
    }

    /**
     * Constructor used outside of CDI.
     *
     * @param lookup the artifact lookup
     */
    public ReferenceMatcher(ArtifactLookup lookup) {
        this.lookup = lookup;
    }

    /**
     * Matches the given references to existing artifacts.
     *
     * @param groupId the group of the artifact being created
     * @param artifactType the artifact type
     * @param references the references found in the content
     * @param excludedNames names of references to skip (e.g. explicitly provided references). A detected
     *        reference is skipped when one of these names equals its full reference, or when the resource part
     *        of one of these names (everything before any {@code #} fragment) equals its resource. So an
     *        explicit {@code money.json} covers {@code money.json#/defs/M}, and vice versa.
     * @return the matched references and the problems
     */
    public ReferenceMatch match(String groupId, String artifactType, Set<ExternalReference> references,
            Set<String> excludedNames) {
        Context ctx = new Context();
        List<ArtifactReferenceDto> matched = new ArrayList<>();
        List<MatchProblem> problems = new ArrayList<>();
        Set<String> excludedResources = excludedNames.stream()
                .filter(Objects::nonNull)
                .map(ReferenceMatcher::stripFragment)
                .collect(Collectors.toSet());
        List<ExternalReference> sorted = references.stream()
                .filter(r -> r.getResource() != null)
                .filter(r -> !excludedNames.contains(r.getFullReference()))
                .filter(r -> !excludedResources.contains(r.getResource()))
                .sorted(Comparator.comparing(ExternalReference::getFullReference))
                .toList();
        for (ExternalReference ref : sorted) {
            String fullReference = ref.getFullReference();
            Outcome outcome = resolve(ctx, groupId, artifactType, ref.getResource());
            if (outcome.problem != null) {
                problems.add(new MatchProblem(fullReference, outcome.problem));
            } else if (outcome.groupId == null) {
                problems.add(new MatchProblem(fullReference,
                        "No matching artifact found in group '" + groupId + "'."));
            } else {
                matched.add(ArtifactReferenceDto.builder().name(fullReference).groupId(outcome.groupId)
                        .artifactId(outcome.artifactId).version(outcome.version).build());
            }
        }
        return new ReferenceMatch(matched, problems);
    }

    private Outcome resolve(Context ctx, String groupId, String artifactType, String resource) {
        if (ArtifactType.AVRO.equals(artifactType)) {
            return resolveAvro(ctx, groupId, resource);
        }
        String withoutFragment = stripFragment(resource);
        if (isAbsoluteUri(withoutFragment)) {
            if (ArtifactType.JSON.equals(artifactType)) {
                StructureSearchResult result = lookup.searchByStructure(groupId,
                        List.of("json:id:" + withoutFragment));
                if (result.truncated()) {
                    return tooManyCandidates(groupId);
                }
                // The structure index is case-insensitive and covers every version of an artifact, so
                // confirm the exact $id against the content of the version that would be pinned.
                return confirmCandidates(ctx, groupId, result.artifactIds(),
                        content -> withoutFragment.equals(jsonSchemaId(content)));
            }
            return Outcome.NOT_FOUND;
        }
        List<String> candidates = new ArrayList<>();
        String normalized = SchemaSourceSet.normalize(resource);
        candidates.add(normalized);
        if (!resource.equals(normalized)) {
            candidates.add(resource);
        }
        for (String artifactId : candidates) {
            Optional<String> version = ctx.latestVersion(groupId, artifactId);
            if (version.isPresent()) {
                return Outcome.found(groupId, artifactId, version.get());
            }
        }
        return Outcome.NOT_FOUND;
    }

    private Outcome resolveAvro(Context ctx, String groupId, String resource) {
        int idx = resource.lastIndexOf('.');
        String ns = idx >= 0 ? resource.substring(0, idx) : null;
        String simple = idx >= 0 ? resource.substring(idx + 1) : resource;

        Optional<String> version = ctx.latestVersion(groupId, resource);
        if (version.isPresent()) {
            return Outcome.found(groupId, resource, version.get());
        }
        if (ns != null) {
            version = ctx.latestVersion(ns, simple);
            if (version.isPresent()) {
                return Outcome.found(ns, simple, version.get());
            }
        }

        // Records are indexed under "avro:name", enums and fixed types under "avro:type".
        Set<String> candidates = new LinkedHashSet<>();
        for (String kind : List.of("avro:name:", "avro:type:")) {
            List<String> filters = new ArrayList<>();
            filters.add(kind + simple);
            if (ns != null) {
                filters.add("avro:namespace:" + ns);
            }
            StructureSearchResult result = lookup.searchByStructure(groupId, filters);
            if (result.truncated()) {
                return tooManyCandidates(groupId);
            }
            candidates.addAll(result.artifactIds());
        }
        return confirmCandidates(ctx, groupId, List.copyOf(candidates),
                content -> resource.equals(avroFullName(content)));
    }

    private static Outcome tooManyCandidates(String groupId) {
        return Outcome.problem("Too many candidate artifacts in group '" + groupId
                + "' to match unambiguously. Provide an explicit reference.");
    }

    /**
     * Resolves each candidate's latest version once, checks the content of that exact version, and keeps the
     * candidates that pass. The version that was checked is the version that gets pinned, even if a new
     * version is published concurrently.
     */
    private Outcome confirmCandidates(Context ctx, String groupId, List<String> artifactIds,
            Predicate<ContentHandle> confirm) {
        Map<String, String> found = new TreeMap<>();
        for (String artifactId : artifactIds) {
            ctx.latestVersion(groupId, artifactId).ifPresent(v -> ctx.content(groupId, artifactId, v)
                    .filter(confirm)
                    .ifPresent(c -> found.put(artifactId, v)));
        }
        if (found.isEmpty()) {
            return Outcome.NOT_FOUND;
        }
        if (found.size() == 1) {
            Map.Entry<String, String> e = found.entrySet().iterator().next();
            return Outcome.found(groupId, e.getKey(), e.getValue());
        }
        List<String> names = found.keySet().stream().map(a -> groupId + "/" + a).toList();
        return Outcome.problem("Ambiguous: matches " + String.join(", ", names) + ".");
    }

    private static String avroFullName(ContentHandle content) {
        try {
            JsonNode node = MAPPER.readTree(content.content());
            String name = node.path("name").asText(null);
            if (name == null) {
                return null;
            }
            if (name.contains(".")) {
                return name;
            }
            String namespace = node.path("namespace").asText("");
            return namespace.isBlank() ? name : namespace + "." + name;
        } catch (IOException e) {
            return null;
        }
    }

    private static String jsonSchemaId(ContentHandle content) {
        try {
            String id = MAPPER.readTree(content.content()).path("$id").asText(null);
            return id == null ? null : stripFragment(id);
        } catch (IOException e) {
            return null;
        }
    }

    private static String stripFragment(String resource) {
        int idx = resource.indexOf('#');
        return idx >= 0 ? resource.substring(0, idx) : resource;
    }

    private static boolean isAbsoluteUri(String value) {
        try {
            return new URI(value).isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Per-call cache of artifact lookups, so that repeated references to the same artifact during a single
     * {@link #match} invocation hit the storage only once. Not thread-safe; never shared across calls.
     */
    private final class Context {
        private final Map<String, Optional<String>> versions = new HashMap<>();
        private final Map<String, Optional<ContentHandle>> contents = new HashMap<>();

        Optional<String> latestVersion(String groupId, String artifactId) {
            return versions.computeIfAbsent(groupId + "\u0000" + artifactId,
                    k -> lookup.latestVersion(groupId, artifactId));
        }

        Optional<ContentHandle> content(String groupId, String artifactId, String version) {
            return contents.computeIfAbsent(groupId + "\u0000" + artifactId + "\u0000" + version,
                    k -> lookup.content(groupId, artifactId, version));
        }
    }

    /**
     * Result of resolving a single reference: either a matched artifact version (group, artifactId and
     * version set), a problem message (e.g. ambiguity), or {@link #NOT_FOUND} when all fields are null.
     */
    private record Outcome(String groupId, String artifactId, String version, String problem) {
        static final Outcome NOT_FOUND = new Outcome(null, null, null, null);

        static Outcome found(String groupId, String artifactId, String version) {
            return new Outcome(groupId, artifactId, version, null);
        }

        static Outcome problem(String problem) {
            return new Outcome(null, null, null, problem);
        }
    }
}
