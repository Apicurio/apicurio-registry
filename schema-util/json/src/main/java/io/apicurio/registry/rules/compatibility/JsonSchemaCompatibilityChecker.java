package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaDiffLibrary;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;

import java.util.Map;
import java.util.Set;

public class JsonSchemaCompatibilityChecker extends AbstractCompatibilityChecker<Difference> {

    @Override
    protected Set<Difference> isBackwardsCompatibleWith(String existing, String proposed, Map<String, ContentHandle> resolvedReferences) {
        return JsonSchemaDiffLibrary.getIncompatibleDifferences(existing, proposed, resolvedReferences);
    }

    @Override
    protected CompatibilityDifference transform(Difference original) {
        return new JsonSchemaCompatibilityDifference(original);
    }
}
