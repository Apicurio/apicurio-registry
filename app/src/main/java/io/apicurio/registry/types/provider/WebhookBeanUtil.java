package io.apicurio.registry.types.provider;

import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.compatibility.SimpleCompatibilityDifference;
import io.apicurio.registry.types.webhooks.beans.ArtifactReference;
import io.apicurio.registry.types.webhooks.beans.IncompatibleDifference;
import io.apicurio.registry.types.webhooks.beans.ResolvedReference;
import io.apicurio.registry.types.webhooks.beans.ResolvedReferenceUrl;
import io.apicurio.registry.types.webhooks.beans.TypedContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WebhookBeanUtil {

    public static TypedContent typedContentToWebhookBean(io.apicurio.registry.content.TypedContent typedContent) {
        TypedContent webhookBean = new TypedContent();
        webhookBean.setContent(typedContent.getContent().content());
        webhookBean.setContentType(typedContent.getContentType());
        return webhookBean;
    }

    public static io.apicurio.registry.content.TypedContent typedContentFromWebhookBean(TypedContent typedContent) {
        return io.apicurio.registry.content.TypedContent.create(typedContent.getContent(), typedContent.getContentType());
    }

    public static List<ResolvedReference> resolvedReferenceListToWebhookBean(Map<String, io.apicurio.registry.content.TypedContent> resolvedReferences) {
        if (resolvedReferences == null || resolvedReferences.isEmpty()) {
            return List.of();
        }
        List<ResolvedReference> webhookBeanList = new ArrayList<>();
        for (Map.Entry<String, io.apicurio.registry.content.TypedContent> entry : resolvedReferences.entrySet()) {
            ResolvedReference ref = new ResolvedReference();
            ref.setName(entry.getKey());
            ref.setContent(entry.getValue().getContent().content());
            ref.setContentType(entry.getValue().getContentType());
            webhookBeanList.add(ref);
        }
        return webhookBeanList;
    }

    public static Set<RuleViolation> ruleViolationSetFromWebhookBean(List<io.apicurio.registry.types.webhooks.beans.RuleViolation> rvs) {
        return rvs.stream().map(rv -> {
            RuleViolation violation = new RuleViolation();
            violation.setContext(rv.getContext());
            violation.setDescription(rv.getDescription());
            return violation;
        }).collect(Collectors.toSet());
    }

    public static List<ArtifactReference> referencesListToWebhookBean(List<io.apicurio.registry.rest.v3.beans.ArtifactReference> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        return references.stream().map(ref -> {
            ArtifactReference artifact = new ArtifactReference();
            artifact.setName(ref.getName());
            artifact.setGroupId(ref.getGroupId());
            artifact.setArtifactId(ref.getArtifactId());
            artifact.setVersion(ref.getVersion());
            return artifact;
        }).collect(Collectors.toList());
    }

    public static List<TypedContent> typedContentListToWebhookBean(List<io.apicurio.registry.content.TypedContent> existingArtifacts) {
        List<TypedContent> rval = new ArrayList<>();
        for (io.apicurio.registry.content.TypedContent existingArtifact : existingArtifacts) {
            rval.add(typedContentToWebhookBean(existingArtifact));
        }
        return rval;
    }

    public static Set<CompatibilityDifference> compatibilityDifferenceSetFromWebhookBean(List<IncompatibleDifference> incompatibleDifferences) {
        return incompatibleDifferences == null ? Set.of() : incompatibleDifferences.stream().map(diff -> {
            return new SimpleCompatibilityDifference(diff.getDescription(), diff.getContext());
        }).collect(Collectors.toSet());
    }

    public static List<ResolvedReferenceUrl> resolvedReferenceUrlListToWebhookBean(Map<String, String> resolvedReferenceUrls) {
        if (resolvedReferenceUrls == null || resolvedReferenceUrls.isEmpty()) {
            return List.of();
        }
        return resolvedReferenceUrls.entrySet().stream().map(e -> {
            ResolvedReferenceUrl ref = new ResolvedReferenceUrl();
            ref.setName(e.getKey());
            ref.setUrl(e.getValue());
            return ref;
        }).collect(Collectors.toList());
    }
}
