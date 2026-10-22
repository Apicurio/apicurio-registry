package io.apicurio.registry.content.extract;

import java.util.Collections;
import java.util.List;

import io.apicurio.registry.content.ContentHandle;

/**
 * Default no-op implementation of {@link StructuredContentExtractor} that returns an empty list. Used for
 * artifact types that don't support structured extraction (e.g. WSDL, XSD, XML, GraphQL, KCONNECT).
 */
public class NoopStructuredContentExtractor implements StructuredContentExtractor {

    public static final StructuredContentExtractor INSTANCE = new NoopStructuredContentExtractor();

    /**
     * Constructor.
     */
    public NoopStructuredContentExtractor() {
    }

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        return Collections.emptyList();
    }
}
