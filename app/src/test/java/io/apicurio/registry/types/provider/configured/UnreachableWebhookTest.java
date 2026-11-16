package io.apicurio.registry.types.provider.configured;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.http.HttpClientService;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An artifact type webhook that cannot be reached must fail the rule (never let content in) with a
 * generic message that does not expose the internal failure.
 */
class UnreachableWebhookTest {

    private static final String INTERNAL_DETAIL = "java.net.UnknownHostException: csv-webhook";

    private static HttpClientService unreachable() {
        HttpClientService httpClientService = mock(HttpClientService.class);
        when(httpClientService.post(anyString(), any(), any())).thenThrow(new RuntimeException(INTERNAL_DETAIL));
        return httpClientService;
    }

    private static WebhookProvider webhook() {
        WebhookProvider provider = new WebhookProvider();
        provider.setType("webhook");
        provider.setUrl("http://csv-webhook:8080/");
        return provider;
    }

    private static TypedContent content() {
        return TypedContent.create(ContentHandle.create("a,b"), "text/csv");
    }

    @Test
    void contentValidatorFailsClosed() {
        ArtifactTypeConfiguration artifactType = new ArtifactTypeConfiguration();
        artifactType.setArtifactType("CSV");
        artifactType.setContentValidator(webhook());
        ConfiguredContentValidator validator = new ConfiguredContentValidator(unreachable(), artifactType);

        RuleViolationException e = assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.FULL, content(), Collections.emptyMap()));
        assertEquals(RuleType.VALIDITY, e.getRuleType());
        assertEquals(1, e.getCauses().size());
        RuleViolation violation = e.getCauses().iterator().next();
        assertEquals("Error invoking Content Validator webhook for 'CSV'.", violation.getDescription());
        assertFalse(e.getDetailMessage().contains("UnknownHostException"));

        RuleViolationException refs = assertThrows(RuleViolationException.class,
                () -> validator.validateReferences(content(), List.of()));
        assertEquals(RuleType.INTEGRITY, refs.getRuleType());
    }

    @Test
    void compatibilityCheckerDoesNotExposeTheFailure() {
        ArtifactTypeConfiguration artifactType = new ArtifactTypeConfiguration();
        artifactType.setArtifactType("CSV");
        artifactType.setCompatibilityChecker(webhook());
        ConfiguredCompatibilityChecker checker = new ConfiguredCompatibilityChecker(unreachable(), artifactType);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(content()), content(), Collections.emptyMap());
        assertEquals(1, result.getIncompatibleDifferences().size());
        CompatibilityDifference difference = result.getIncompatibleDifferences().iterator().next();
        assertEquals("Error invoking Compatibility Checker webhook for 'CSV'.",
                difference.asRuleViolation().getDescription());
    }

    private static ArtifactTypeConfiguration csvType() {
        ArtifactTypeConfiguration artifactType = new ArtifactTypeConfiguration();
        artifactType.setArtifactType("CSV");
        return artifactType;
    }

    private static void assertGenericFailure(String provider, Executable call) {
        ArtifactTypeWebhookException e = assertThrows(ArtifactTypeWebhookException.class, call);
        assertEquals("Error invoking " + provider + " webhook for 'CSV'.", e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    void contentCanonicalizerFailsInsteadOfReturningTheContent() {
        ArtifactTypeConfiguration artifactType = csvType();
        artifactType.setContentCanonicalizer(webhook());
        ConfiguredContentCanonicalizer canonicalizer = new ConfiguredContentCanonicalizer(unreachable(), artifactType);

        assertGenericFailure("Content Canonicalizer",
                () -> canonicalizer.canonicalize(content(), Collections.emptyMap()));
    }

    @Test
    void contentDereferencerFailsInsteadOfReturningTheContent() {
        ArtifactTypeConfiguration artifactType = csvType();
        artifactType.setContentDereferencer(webhook());
        ConfiguredContentDereferencer dereferencer = new ConfiguredContentDereferencer(unreachable(), artifactType);

        assertGenericFailure("Content Dereferencer",
                () -> dereferencer.dereference(content(), Collections.emptyMap()));
        assertGenericFailure("Content Dereferencer",
                () -> dereferencer.rewriteReferences(content(), Collections.emptyMap()));
    }

    @Test
    void referenceFinderFailsWithAGenericMessage() {
        ArtifactTypeConfiguration artifactType = csvType();
        artifactType.setReferenceFinder(webhook());
        ConfiguredReferenceFinder finder = new ConfiguredReferenceFinder(unreachable(), artifactType);

        assertGenericFailure("Reference Finder", () -> finder.findExternalReferences(content()));
    }
}
