package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.JavaClassProvider;
import io.apicurio.registry.config.artifactTypes.ScriptProvider;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.http.HttpClientService;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.script.ScriptingService;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.webhooks.beans.ContentValidatorRequest;
import io.apicurio.registry.types.webhooks.beans.ContentValidatorResponse;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfiguredContentValidator extends AbstractConfiguredArtifactTypeUtil<ContentValidator> implements ContentValidator {

    public ConfiguredContentValidator(HttpClientService httpClientService, ScriptingService scriptingService, ArtifactTypeConfiguration artifactType) {
        super(httpClientService, scriptingService, artifactType, artifactType.getContentValidator());
    }

    @Override
    public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        this.delegate.validate(level, content, resolvedReferences);
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references) throws RuleViolationException {
        this.delegate.validateReferences(content, references);
    }

    @Override
    protected ContentValidator createJavaClassDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
        return new ConfiguredContentValidator.JavaClassContentValidatorDelegate(artifactType, provider);
    }

    private class JavaClassContentValidatorDelegate extends AbstractJavaClassDelegate implements ContentValidator {
        public JavaClassContentValidatorDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
            super(artifactType, provider);
        }

        @Override
        public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
            this.instance.validate(level, content, resolvedReferences);
        }

        @Override
        public void validateReferences(TypedContent content, List<ArtifactReference> references) throws RuleViolationException {
            this.instance.validateReferences(content, references);
        }
    }

    @Override
    protected ContentValidator createWebhookDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) throws Exception {
        return new ConfiguredContentValidator.WebhookContentValidatorDelegate(artifactType, provider);
    }

    private class WebhookContentValidatorDelegate extends AbstractWebhookDelegate<ContentValidatorRequest, ContentValidatorResponse> implements ContentValidator {

        protected WebhookContentValidatorDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) {
            super(artifactType, provider);
        }

        @Override
        public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
            // Create the request payload object
            ContentValidatorRequest requestBody = createValidateRequest(level, content, resolvedReferences);

            try {
                ContentValidatorResponse responseBody = invokeHook(requestBody, ContentValidatorResponse.class);
                List<io.apicurio.registry.types.webhooks.beans.RuleViolation> rvs = responseBody.getRuleViolations();
                if (rvs != null && !rvs.isEmpty()) {
                    Set<RuleViolation> violations = WebhookBeanUtil.ruleViolationSetFromWebhookBean(rvs);
                    throw new RuleViolationException("Validation failed.", RuleType.VALIDITY, level.name(), violations);
                }
            } catch (RuleViolationException rve) {
                throw rve;
            } catch (Throwable e) {
                log.error("Error invoking webhook", e);
            }
        }

        @Override
        public void validateReferences(TypedContent content, List<ArtifactReference> references) throws RuleViolationException {
            // Create the request payload object
            ContentValidatorRequest requestBody = createValidateRefsRequest(content, references);

            try {
                ContentValidatorResponse responseBody = invokeHook(requestBody, ContentValidatorResponse.class);
                List<io.apicurio.registry.types.webhooks.beans.RuleViolation> rvs = responseBody.getRuleViolations();
                if (rvs != null && !rvs.isEmpty()) {
                    Set<RuleViolation> violations = WebhookBeanUtil.ruleViolationSetFromWebhookBean(rvs);
                    throw new RuleViolationException("Unmapped reference(s) detected.", RuleType.INTEGRITY,
                            IntegrityLevel.ALL_REFS_MAPPED.name(), violations);
                }
            } catch (RuleViolationException rve) {
                throw rve;
            } catch (Throwable e) {
                log.error("Error invoking webhook", e);
            }
        }

    }

    @Override
    protected ContentValidator createScriptDelegate(ArtifactTypeConfiguration artifactType, ScriptProvider provider) throws Exception {
        return new ConfiguredContentValidator.ScriptContentValidatorDelegate(artifactType, provider);
    }

    private class ScriptContentValidatorDelegate extends AbstractScriptDelegate<ContentValidatorRequest, ContentValidatorResponse> implements ContentValidator {

        protected ScriptContentValidatorDelegate(ArtifactTypeConfiguration artifactType, ScriptProvider provider) {
            super(artifactType, provider);
        }

        @Override
        public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
            ContentValidatorRequest requestBody = createValidateRequest(level, content, resolvedReferences);

            try {
                ContentValidatorResponse responseBody = executeScript(requestBody, ContentValidatorResponse.class);
                List<io.apicurio.registry.types.webhooks.beans.RuleViolation> rvs = responseBody.getRuleViolations();
                if (rvs != null && !rvs.isEmpty()) {
                    Set<RuleViolation> violations = WebhookBeanUtil.ruleViolationSetFromWebhookBean(rvs);
                    throw new RuleViolationException("Validation failed.", RuleType.VALIDITY, level.name(), violations);
                }
            } catch (RuleViolationException rve) {
                throw rve;
            } catch (Throwable e) {
                log.error("Error invoking webhook", e);
            }
        }

        @Override
        public void validateReferences(TypedContent content, List<ArtifactReference> references) throws RuleViolationException {
            ContentValidatorRequest requestBody = createValidateRefsRequest(content, references);

            try {
                ContentValidatorResponse responseBody = executeScript(requestBody, ContentValidatorResponse.class);
                List<io.apicurio.registry.types.webhooks.beans.RuleViolation> rvs = responseBody.getRuleViolations();
                if (rvs != null && !rvs.isEmpty()) {
                    Set<RuleViolation> violations = WebhookBeanUtil.ruleViolationSetFromWebhookBean(rvs);
                    throw new RuleViolationException("Unmapped reference(s) detected.", RuleType.INTEGRITY,
                            IntegrityLevel.ALL_REFS_MAPPED.name(), violations);
                }
            } catch (RuleViolationException rve) {
                throw rve;
            } catch (Throwable e) {
                log.error("Error invoking webhook", e);
            }
        }

    }

    private static @NotNull ContentValidatorRequest createValidateRequest(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) {
        ContentValidatorRequest requestBody = new ContentValidatorRequest();
        requestBody.setFunction(ContentValidatorRequest.Function.validate);
        requestBody.setLevel(level.name());
        requestBody.setContent(WebhookBeanUtil.typedContentToWebhookBean(content));
        requestBody.setResolvedReferences(WebhookBeanUtil.resolvedReferenceListToWebhookBean(resolvedReferences));
        return requestBody;
    }

    private static @NotNull ContentValidatorRequest createValidateRefsRequest(TypedContent content, List<ArtifactReference> references) {
        ContentValidatorRequest requestBody = new ContentValidatorRequest();
        requestBody.setFunction(ContentValidatorRequest.Function.validateReferences);
        requestBody.setContent(WebhookBeanUtil.typedContentToWebhookBean(content));
        requestBody.setReferences(WebhookBeanUtil.referencesListToWebhookBean(references));
        return requestBody;
    }

}
