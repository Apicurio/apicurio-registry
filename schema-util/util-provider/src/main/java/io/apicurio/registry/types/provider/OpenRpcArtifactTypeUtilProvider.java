package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.refs.DefaultReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.openrpc.content.OpenRpcContentAccepter;
import io.apicurio.registry.openrpc.content.canon.OpenRpcContentCanonicalizer;
import io.apicurio.registry.openrpc.content.dereference.OpenRpcDereferencer;
import io.apicurio.registry.openrpc.content.extract.OpenRpcContentExtractor;
import io.apicurio.registry.openrpc.content.refs.OpenRpcReferenceFinder;
import io.apicurio.registry.openrpc.rules.validity.OpenRpcContentValidator;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

import java.util.Set;

/**
 * Artifact type utility provider for OpenRPC specifications.
 *
 * OpenRPC is a specification for JSON-RPC APIs, similar to how OpenAPI describes RESTful APIs.
 *
 * @see <a href="https://open-rpc.org/">OpenRPC Specification</a>
 */
public class OpenRpcArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.OPENRPC;
    }

    @Override
    public Set<String> getContentTypes() {
        return Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML);
    }

    @Override
    protected ContentAccepter createContentAccepter() {
        return new OpenRpcContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return NoopCompatibilityChecker.INSTANCE;
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new OpenRpcContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new OpenRpcContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new OpenRpcContentExtractor();
    }

    @Override
    protected ContentDereferencer createContentDereferencer() {
        return new OpenRpcDereferencer();
    }

    @Override
    protected ReferenceFinder createReferenceFinder() {
        return new OpenRpcReferenceFinder();
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return true;
    }

    @Override
    protected ReferenceArtifactIdentifierExtractor createReferenceArtifactIdentifierExtractor() {
        return new DefaultReferenceArtifactIdentifierExtractor();
    }

}
