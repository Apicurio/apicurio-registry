package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.JavaClassProvider;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.types.webhooks.beans.CompatibilityCheckerRequest;
import io.apicurio.registry.types.webhooks.beans.CompatibilityCheckerResponse;
import io.apicurio.registry.types.webhooks.beans.IncompatibleDifference;

import java.util.List;
import java.util.Map;

public class ConfiguredCompatibilityChecker extends AbstractConfiguredArtifactTypeUtil<CompatibilityChecker> implements CompatibilityChecker {

    public ConfiguredCompatibilityChecker(ArtifactTypeConfiguration artifactType) {
        super(artifactType, artifactType.getCompatibilityChecker());
    }

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<TypedContent> existingArtifacts, TypedContent proposedArtifact, Map<String, TypedContent> resolvedReferences) {
        return this.delegate.testCompatibility(compatibilityLevel, existingArtifacts, proposedArtifact, resolvedReferences);
    }

    @Override
    protected CompatibilityChecker createJavaClassDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
        return new ConfiguredCompatibilityChecker.JavaClassCompatibilityCheckerDelegate(artifactType, provider);
    }

    private class JavaClassCompatibilityCheckerDelegate extends AbstractJavaClassDelegate implements CompatibilityChecker {
        public JavaClassCompatibilityCheckerDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
            super(artifactType, provider);
        }

        @Override
        public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<TypedContent> existingArtifacts, TypedContent proposedArtifact, Map<String, TypedContent> resolvedReferences) {
            return this.instance.testCompatibility(compatibilityLevel, existingArtifacts, proposedArtifact, resolvedReferences);
        }
    }

    @Override
    protected CompatibilityChecker createWebhookDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) throws Exception {
        return new ConfiguredCompatibilityChecker.WebhookCompatibilityCheckerDelegate(artifactType, provider);
    }

    private class WebhookCompatibilityCheckerDelegate extends AbstractWebhookDelegate<CompatibilityCheckerRequest, CompatibilityCheckerResponse> implements CompatibilityChecker {

        protected WebhookCompatibilityCheckerDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) {
            super(artifactType, provider);
        }

        @Override
        public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<TypedContent> existingArtifacts,
                                                              TypedContent proposedArtifact, Map<String, TypedContent> resolvedReferences) {
            // Create the request payload object
            CompatibilityCheckerRequest requestBody = new CompatibilityCheckerRequest();
            requestBody.setLevel(compatibilityLevel.name());
            requestBody.setExistingArtifacts(WebhookBeanUtil.typedContentListToWebhookBean(existingArtifacts));
            requestBody.setProposedArtifact(WebhookBeanUtil.typedContentToWebhookBean(proposedArtifact));
            requestBody.setResolvedReferences(WebhookBeanUtil.resolvedReferenceListToWebhookBean(resolvedReferences));

            try {
                CompatibilityCheckerResponse responseBody = invokeHook(requestBody, CompatibilityCheckerResponse.class);
                List<IncompatibleDifference> incompatibleDifferences = responseBody.getIncompatibleDifferences();
                if (incompatibleDifferences == null || incompatibleDifferences.isEmpty()) {
                    return CompatibilityExecutionResult.compatible();
                } else {
                    return CompatibilityExecutionResult.incompatibleOrEmpty(WebhookBeanUtil.compatibilityDifferenceSetFromWebhookBean(incompatibleDifferences));
                }
            } catch (Throwable e) {
                log.error("Error invoking webhook", e);
                return CompatibilityExecutionResult.incompatible(
                        "Error invoking Compatibility Checker webhook for '" + this.artifactType.getArtifactType() + "': " + e.getMessage());
            }
        }

    }
}
