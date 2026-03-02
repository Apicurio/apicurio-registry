package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.NoopStructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;

import java.util.Set;

/**
 * Interface providing different utils per artifact type * compatibility checker * content canonicalizer *
 * content validator * rules * etc ...
 */
public interface ArtifactTypeUtilProvider {

    String getArtifactType();

    Set<String> getContentTypes();

    ContentAccepter getContentAccepter();

    ContentCanonicalizer getContentCanonicalizer();

    ContentExtractor getContentExtractor();

    ContentValidator getContentValidator();

    CompatibilityChecker getCompatibilityChecker();

    ContentDereferencer getContentDereferencer();

    ReferenceFinder getReferenceFinder();

    boolean supportsReferencesWithContext();

    ReferenceArtifactIdentifierExtractor getReferenceArtifactIdentifierExtractor();

    /**
     * Returns the structured content extractor for this artifact type. The extractor identifies
     * searchable structured elements within the artifact content (e.g., schema names, paths,
     * fields). Returns a no-op extractor by default; override in providers that support
     * structured extraction.
     *
     * @return a structured content extractor for this artifact type
     */
    default StructuredContentExtractor getStructuredContentExtractor() {
        return NoopStructuredContentExtractor.INSTANCE;
    }
}
