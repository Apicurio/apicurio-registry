package io.apicurio.registry.openapi.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.AbstractCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.SimpleCompatibilityDifference;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.Changed;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpenApiCompatibilityChecker extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    @Override
    protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(final String existing,
            final String proposed, final Map<String, TypedContent> resolvedReferences) {
        ChangedOpenApi diff = OpenApiCompare.fromContents(existing, proposed);

        Stream<SimpleCompatibilityDifference> incompatibleOperations = diff.getChangedOperations().stream()
                .filter(Objects::nonNull).filter(Changed::isIncompatible)
                .map(operation -> new SimpleCompatibilityDifference("Incompatible operation",
                        operation.getPathUrl()));

        Stream<SimpleCompatibilityDifference> incompatibleSchemas = diff.getChangedSchemas().stream()
                .filter(Objects::nonNull).filter(Changed::isIncompatible)
                .map(schema -> new SimpleCompatibilityDifference("Incompatible schema",
                        schema.getNewSchema().getName()));

        Stream<SimpleCompatibilityDifference> missingEndpoints = diff.getMissingEndpoints().stream()
                .filter(Objects::nonNull)
                .map(endpoint -> new SimpleCompatibilityDifference("Missing endpoint",
                        endpoint.getPathUrl()));

        return Stream.of(incompatibleOperations, incompatibleSchemas, missingEndpoints)
                .flatMap(stream -> stream).collect(Collectors.toSet());
    }
}
