package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.AgentCardContentAccepter;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.json.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.NoopContentDereferencer;
import io.apicurio.registry.content.extract.AgentCardContentExtractor;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.refs.DefaultReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.NoOpReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.AgentCardContentValidator;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

import java.util.Set;

/**
 * Artifact type utility provider for A2A Agent Card artifacts.
 *
 * Agent Cards are JSON documents that describe AI agents following the A2A (Agent2Agent) protocol.
 * They contain metadata about an agent's capabilities, skills, and communication endpoints.
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol</a>
 */
public class AgentCardArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.AGENT_CARD;
    }

    @Override
    public Set<String> getContentTypes() {
        return Set.of(ContentTypes.APPLICATION_JSON);
    }

    @Override
    protected ContentAccepter createContentAccepter() {
        return new AgentCardContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        // For MVP, no compatibility checking
        // Future: implement Agent Card compatibility rules
        return NoopCompatibilityChecker.INSTANCE;
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        // Use JSON canonicalizer for consistent formatting
        return new JsonContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new AgentCardContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new AgentCardContentExtractor();
    }

    @Override
    protected ContentDereferencer createContentDereferencer() {
        // Agent Cards don't support references in MVP
        return NoopContentDereferencer.INSTANCE;
    }

    @Override
    protected ReferenceFinder createReferenceFinder() {
        // Agent Cards don't support references in MVP
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
}
