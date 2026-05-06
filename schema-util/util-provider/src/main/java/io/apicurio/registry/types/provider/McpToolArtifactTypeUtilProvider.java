package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.McpToolContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.NoopContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.McpToolContentExtractor;
import io.apicurio.registry.content.extract.McpToolStructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.refs.DefaultReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.NoOpReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.json.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.McpToolCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.McpToolContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

import java.util.Set;

/**
 * Artifact type utility provider for MCP (Model Context Protocol) tool definition artifacts.
 *
 * MCP tools are JSON documents that describe tools available to AI agents following the MCP specification.
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/tools/">MCP Tools</a>
 */
public class McpToolArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.MCP_TOOL;
    }

    @Override
    public Set<String> getContentTypes() {
        return Set.of(ContentTypes.APPLICATION_JSON);
    }

    @Override
    protected ContentAccepter createContentAccepter() {
        return new McpToolContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return new McpToolCompatibilityChecker();
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new JsonContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new McpToolContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new McpToolContentExtractor();
    }

    @Override
    protected ContentDereferencer createContentDereferencer() {
        return NoopContentDereferencer.INSTANCE;
    }

    @Override
    protected ReferenceFinder createReferenceFinder() {
        return NoOpReferenceFinder.INSTANCE;
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return false;
    }

    @Override
    protected ReferenceArtifactIdentifierExtractor createReferenceArtifactIdentifierExtractor() {
        return new DefaultReferenceArtifactIdentifierExtractor();
    }

    @Override
    protected StructuredContentExtractor createStructuredContentExtractor() {
        return new McpToolStructuredContentExtractor();
    }
}
