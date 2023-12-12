package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;

/**
 * Interface providing different utils per artifact type * compatibility checker * content canonicalizer *
 * content validator * rules * etc ...
 */
public interface ArtifactTypeUtilProvider {
    String getArtifactType();

    CompatibilityChecker getCompatibilityChecker();

    ContentCanonicalizer getContentCanonicalizer();

    ContentValidator getContentValidator();

    ContentExtractor getContentExtractor();

    ContentDereferencer getContentDereferencer();

    ReferenceFinder getReferenceFinder();
}
