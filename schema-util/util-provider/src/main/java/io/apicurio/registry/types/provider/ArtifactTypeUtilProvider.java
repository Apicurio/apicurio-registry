package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;

import java.util.Map;

/**
 * Interface providing different utils per artifact type * compatibility checker * content canonicalizer *
 * content validator * rules * etc ...
 */
public interface ArtifactTypeUtilProvider {
    String getArtifactType();

    /**
     * Returns true if the given content is accepted as handled by the provider. Useful to know if e.g. some
     * bit of content is an AVRO or OPENAPI.
     */
    boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences);

    CompatibilityChecker getCompatibilityChecker();

    ContentCanonicalizer getContentCanonicalizer();

    ContentValidator getContentValidator();

    ContentExtractor getContentExtractor();

    ContentDereferencer getContentDereferencer();

    ReferenceFinder getReferenceFinder();

    boolean supportsReferencesWithContext();

    ReferenceArtifactIdentifierExtractor getReferenceArtifactIdentifierExtractor();
}
