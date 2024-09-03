package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.Changed;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.openapitools.openapidiff.core.model.ChangedOperation;
import org.openapitools.openapidiff.core.model.ChangedSchema;
import org.openapitools.openapidiff.core.model.Endpoint;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpenApiCompatibilityChecker extends AbstractCompatibilityChecker<Object> {

    @Override
    protected Set<Object> isBackwardsCompatibleWith(final String existing, final String proposed,
            final Map<String, TypedContent> resolvedReferences) {
        ChangedOpenApi diff = OpenApiCompare.fromContents(existing, proposed);

        List<ChangedOperation> incompatibleOperations = diff.getChangedOperations().stream()
                .filter(Objects::nonNull).filter(Changed::isIncompatible).toList();

        List<ChangedSchema> incompatibleSchemas = diff.getChangedSchemas().stream().filter(Objects::nonNull)
                .filter(Changed::isIncompatible).toList();

        List<Endpoint> missingEndpoints = diff.getMissingEndpoints().stream().filter(Objects::nonNull)
                .toList();

        return Stream.of(incompatibleOperations, incompatibleSchemas, missingEndpoints).flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    @Override
    protected CompatibilityDifference transform(final Object original) {
        if (original instanceof ChangedOperation) {
            return new SimpleCompatibilityDifference("Incompatible operation",
                    ((ChangedOperation) original).getPathUrl());
        } else if (original instanceof ChangedSchema) {
            return new SimpleCompatibilityDifference("Incompatible schema",
                    ((ChangedSchema) original).getNewSchema().getName());
        } else if (original instanceof Endpoint) {
            return new SimpleCompatibilityDifference("Missing endpoint", ((Endpoint) original).getPathUrl());
        }

        throw new IllegalArgumentException("Unsupported type: " + original.getClass().getName());
    }
}
