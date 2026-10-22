package io.apicurio.registry.json.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaDiffLibrary;
import io.apicurio.registry.rules.compatibility.AbstractCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.SimpleCompatibilityDifference;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class JsonSchemaCompatibilityChecker extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    @Override
    protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(String existing, String proposed,
            Map<String, TypedContent> resolvedReferences) {
        return JsonSchemaDiffLibrary.getIncompatibleDifferences(existing, proposed, resolvedReferences)
                .stream()
                .map(difference -> new SimpleCompatibilityDifference(
                        difference.getDiffType().getDescription(), difference.getPathUpdated()))
                .collect(Collectors.toSet());
    }
}
