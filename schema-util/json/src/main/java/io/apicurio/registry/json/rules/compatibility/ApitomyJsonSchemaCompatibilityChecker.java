package io.apicurio.registry.json.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.AbstractCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apitomy.datamodels.jsonschema.compat.JsonSchemaCompatibilityChecker;
import io.apitomy.datamodels.jsonschema.ref.AnchorFragmentResolver;
import io.apitomy.datamodels.jsonschema.ref.JsonSchemaRefResolverChain;
import io.apitomy.datamodels.jsonschema.ref.PointerFragmentResolver;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JSON Schema compatibility checker using the Apitomy Data Models library
 * instead of the everit-json-schema library.
 */
public class ApitomyJsonSchemaCompatibilityChecker extends AbstractCompatibilityChecker<ApitomyCompatibilityDifference> {

    @Override
    protected Set<ApitomyCompatibilityDifference> isBackwardsCompatibleWith(String existing, String proposed,
            Map<String, TypedContent> resolvedReferences) {
        var chain = JsonSchemaRefResolverChain.builder()
                .addFragmentResolver(new PointerFragmentResolver())
                .addFragmentResolver(new AnchorFragmentResolver())
                .addResourceResolver(new RegistryResourceResolver(resolvedReferences))
                .build();

        return JsonSchemaCompatibilityChecker
                .checkBackwardCompatibility(existing, proposed, chain)
                .getIncompatibleDifferences().stream()
                .map(ApitomyCompatibilityDifference::new)
                .collect(Collectors.toSet());
    }

    @Override
    protected CompatibilityDifference transform(ApitomyCompatibilityDifference original) {
        return original;
    }
}
