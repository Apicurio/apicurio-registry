package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.PromptTemplateContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.YamlContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.PromptTemplateDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.PromptTemplateContentExtractor;
import io.apicurio.registry.content.extract.PromptTemplateStructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.refs.DefaultReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.PromptTemplateReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.PromptTemplateCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.PromptTemplateContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

import java.util.Set;

/**
 * Artifact type utility provider for Prompt Template artifacts.
 */
public class PromptTemplateArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public String getArtifactType() {
        return ArtifactType.PROMPT_TEMPLATE;
    }

    @Override
    public Set<String> getContentTypes() {
        return Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML,
                ContentTypes.TEXT_PROMPT_TEMPLATE);
    }

    @Override
    protected ContentAccepter createContentAccepter() {
        return new PromptTemplateContentAccepter();
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return new PromptTemplateCompatibilityChecker();
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new YamlContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new PromptTemplateContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new PromptTemplateContentExtractor();
    }

    @Override
    protected ContentDereferencer createContentDereferencer() {
        return new PromptTemplateDereferencer();
    }

    @Override
    protected ReferenceFinder createReferenceFinder() {
        return new PromptTemplateReferenceFinder();
    }

    @Override
    public boolean supportsReferencesWithContext() {
        return true;
    }

    @Override
    protected ReferenceArtifactIdentifierExtractor createReferenceArtifactIdentifierExtractor() {
        return new DefaultReferenceArtifactIdentifierExtractor();
    }

    @Override
    protected StructuredContentExtractor createStructuredContentExtractor() {
        return new PromptTemplateStructuredContentExtractor();
    }
}
