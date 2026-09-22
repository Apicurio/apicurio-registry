package io.apicurio.registry.json.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.AbstractCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.SimpleCompatibilityDifference;
import io.apitomy.datamodels.jsonschema.compat.JsonSchemaCompatibilityChecker;
import io.apitomy.datamodels.jsonschema.ref.AnchorFragmentResolver;
import io.apitomy.datamodels.jsonschema.ref.JsonSchemaRefDereferencer;
import io.apitomy.datamodels.jsonschema.ref.JsonSchemaRefResolverChain;
import io.apitomy.datamodels.jsonschema.ref.PointerFragmentResolver;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JSON Schema compatibility checker using the Apitomy Data Models library
 * instead of the everit-json-schema library.
 */
public class ApitomyJsonSchemaCompatibilityChecker extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    @Override
    protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(String existing, String proposed,
            Map<String, TypedContent> resolvedReferences) {
        var chain = JsonSchemaRefResolverChain.builder()
                .addFragmentResolver(new PointerFragmentResolver())
                .addFragmentResolver(new AnchorFragmentResolver())
                .addResourceResolver(new RegistryResourceResolver(resolvedReferences))
                .build();

        // 4.0 takes a dereferencer rather than a resolver directly: references are inlined
        // before the comparison runs, and the resolver is what the dereferencer consults.
        var dereferencer = JsonSchemaRefDereferencer.builder()
                .refResolver(chain)
                .build();

        // Cross-version checking is off by default in 4.0, which would make an artifact whose
        // $schema changed between versions fail with IllegalArgumentException instead of
        // producing a compatibility result. The 3.1.x entry point compared across drafts
        // without complaint, so this preserves the behaviour Registry had.
        var checker = JsonSchemaCompatibilityChecker.builder()
                .dereferencer(dereferencer)
                .allowCrossVersionChecking(true)
                .build();

        return checker.checkBackward(existing, proposed)
                .getIncompatibleDifferences().stream()
                .map(difference -> new SimpleCompatibilityDifference(difference.getDiffType().name(),
                        difference.getPathUpdated()))
                .collect(Collectors.toSet());
    }
}
