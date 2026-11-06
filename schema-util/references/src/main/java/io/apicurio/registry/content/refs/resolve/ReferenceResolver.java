package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Computes a {@link RegistrationPlan} for a set of root schemas, transitively resolving their references
 * against local sources, existing artifacts, and (optionally) registry URLs.
 */
public final class ReferenceResolver {

    private final SourceIndex index;
    private final TypeDetector detector;
    private final PlanOptions options;
    private final Map<SchemaSource, RootArtifact> rootsBySource = new IdentityHashMap<>();
    private final Map<SchemaSource, PlannedArtifact> memo = new IdentityHashMap<>();
    private final Set<SchemaSource> stack = new LinkedHashSet<>();
    private final List<PlannedArtifact> output = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<UnresolvedReference> unresolved = new ArrayList<>();

    private ReferenceResolver(SourceIndex index, TypeDetector detector, PlanOptions options) {
        this.index = index;
        this.detector = detector;
        this.options = options;
    }

    /**
     * Plans the registration of the given roots.
     *
     * @param roots the root artifacts to register
     * @param sources the available schema sources
     * @param options the planning options
     * @return the plan, with dependencies ordered before their dependents
     * @throws IllegalArgumentException if a root source is missing, its type cannot be determined, or a
     *         source cannot be scanned for references
     * @throws ReferenceCycleException if references form a cycle
     * @throws UnresolvedReferencesException if any reference could not be resolved
     */
    public static RegistrationPlan plan(List<RootArtifact> roots, SchemaSourceSet sources, PlanOptions options) {
        TypeDetector detector = new TypeDetector(options.providerFactory());
        SourceIndex index = SourceIndex.build(sources, options.existingArtifacts(), detector);
        ReferenceResolver resolver = new ReferenceResolver(index, detector, options);
        List<SchemaSource> rootSources = new ArrayList<>();
        for (RootArtifact root : roots) {
            SchemaSource source = sources.byLocation(root.location()).orElseThrow(
                    () -> new IllegalArgumentException("Root schema not found: " + root.location()));
            resolver.rootsBySource.putIfAbsent(source, root);
            rootSources.add(source);
        }
        for (SchemaSource source : rootSources) {
            resolver.visit(source, null, null, null);
        }
        if (!resolver.unresolved.isEmpty()) {
            throw new UnresolvedReferencesException(resolver.unresolved);
        }
        return new RegistrationPlan(resolver.output, resolver.warnings);
    }

    private PlannedArtifact visit(SchemaSource source, String resource, String parentType,
            ArtifactCoordinates parentCoordinates) {
        PlannedArtifact done = memo.get(source);
        if (done != null) {
            return done;
        }
        PlannedArtifact artifact = create(source, resource, parentType, parentCoordinates);
        stack.add(source);
        ArtifactTypeUtilProvider provider = options.providerFactory()
                .getArtifactTypeProvider(artifact.artifactType());
        List<ExternalReference> refs;
        try {
            refs = new ArrayList<>(provider.getReferenceFinder().findExternalReferences(source.content()));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unable to find references in " + source.location() + ": "
                    + e.getMessage(), e);
        }
        refs.sort(Comparator.comparing(ExternalReference::getFullReference));
        List<PlannedReference> planned = new ArrayList<>();
        for (ExternalReference ref : refs) {
            if (ref.getResource() == null) {
                continue;
            }
            String fullReference = ref.getFullReference();
            Optional<LookupResult> hit = index.lookup(ref.getResource(), source);
            if (hit.isPresent() && hit.get().isExisting()) {
                planned.add(new PlannedReference(fullReference, hit.get().existing()));
            } else if (hit.isPresent()) {
                SchemaSource target = hit.get().source().source();
                if (stack.contains(target)) {
                    List<String> cycle = new ArrayList<>();
                    stack.forEach(s -> cycle.add(s.location()));
                    cycle.add(target.location());
                    throw new ReferenceCycleException(cycle);
                }
                planned.add(new PlannedReference(fullReference,
                        visit(target, ref.getResource(), artifact.artifactType(), artifact.coordinates())));
            } else {
                Optional<ExistingArtifact> byUrl = options.urlResolver().flatMap(r -> r.resolve(fullReference));
                if (byUrl.isPresent()) {
                    planned.add(new PlannedReference(fullReference, byUrl.get()));
                } else if (isAbsoluteUri(fullReference)) {
                    warnings.add("Skipping external reference not managed by Apicurio Registry: " + fullReference);
                } else {
                    unresolved.add(new UnresolvedReference(source.location(), fullReference));
                }
            }
        }
        artifact.setReferences(planned);
        stack.remove(source);
        memo.put(source, artifact);
        output.add(artifact);
        return artifact;
    }

    private PlannedArtifact create(SchemaSource source, String resource, String parentType,
            ArtifactCoordinates parentCoordinates) {
        String sourceContentType = source.content().getContentType();
        RootArtifact root = rootsBySource.get(source);
        if (root != null) {
            String type = root.artifactType() != null ? root.artifactType()
                    : detector.detect(source).orElseThrow(() -> new IllegalArgumentException(
                            "Unable to detect artifact type of " + source.location()));
            String contentType = root.contentType() != null ? root.contentType() : sourceContentType;
            return new PlannedArtifact(source, new ArtifactCoordinates(root.groupId(), root.artifactId()), type,
                    contentType, root);
        }
        IndexedSource indexed = index.bySource(source).orElseThrow(() -> new IllegalArgumentException(
                "Unable to detect artifact type of " + source.location()));
        ArtifactCoordinates coordinates = options.namingStrategy()
                .name(new NamingContext(indexed, resource, parentType, parentCoordinates));
        return new PlannedArtifact(source, coordinates, indexed.artifactType(), sourceContentType, null);
    }

    private static boolean isAbsoluteUri(String value) {
        try {
            return URI.create(value).isAbsolute();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
