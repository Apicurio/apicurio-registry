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

        var result = checker.checkBackward(existing, proposed);

        // AbstractCompatibilityChecker separates "not compatible" — report differences — from
        // "compatibility could not be determined" — throw. An unresolved reference is the second:
        // the sub-schemas behind it were never compared, so any verdict understates what was
        // checked. Without this the caller is told whatever incidental difference the unresolved
        // $ref happened to produce, which for a mistyped reference is a property-narrowing report
        // that says nothing about the real problem. The legacy checker throws here too.
        if (result.hasUnsupportedFeatures()) {
            throw new IllegalStateException("Compatibility could not be determined: "
                    + String.join("; ", result.getUnsupportedFeatures()));
        }

        return result.getIncompatibleDifferences().stream()
                .map(difference -> new SimpleCompatibilityDifference(difference.getDiffType().name(),
                        difference.getPathUpdated().toString()))
                .collect(Collectors.toSet());
    }
}
