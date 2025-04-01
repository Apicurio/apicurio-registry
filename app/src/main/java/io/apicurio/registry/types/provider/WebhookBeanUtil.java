package io.apicurio.registry.types.provider;

import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.types.webhooks.beans.ArtifactReference;
import io.apicurio.registry.types.webhooks.beans.ResolvedReference;
import io.apicurio.registry.types.webhooks.beans.TypedContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WebhookBeanUtil {

    public static TypedContent toWebhookBean(io.apicurio.registry.content.TypedContent typedContent) {
        TypedContent webhookBean = new TypedContent();
        webhookBean.setContent(typedContent.getContent().content());
        webhookBean.setContentType(typedContent.getContentType());
        return webhookBean;
    }

    public static io.apicurio.registry.content.TypedContent fromWebhookBean(TypedContent typedContent) {
        return io.apicurio.registry.content.TypedContent.create(typedContent.getContent(), typedContent.getContentType());
    }

    public static List<ResolvedReference> toWebhookBean(Map<String, io.apicurio.registry.content.TypedContent> resolvedReferences) {
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

    public static Set<RuleViolation> fromWebhookBean(List<io.apicurio.registry.types.webhooks.beans.RuleViolation> rvs) {
        return rvs.stream().map(rv -> {
            RuleViolation violation = new RuleViolation();
            violation.setContext(rv.getContext());
            violation.setDescription(rv.getDescription());
            return violation;
        }).collect(Collectors.toSet());
    }

    public static List<ArtifactReference> toWebhookBean(List<io.apicurio.registry.rest.v3.beans.ArtifactReference> references) {
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
}
