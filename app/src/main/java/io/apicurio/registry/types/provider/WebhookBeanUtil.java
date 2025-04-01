package io.apicurio.registry.types.provider;

import io.apicurio.registry.types.webhooks.beans.ResolvedReference;
import io.apicurio.registry.types.webhooks.beans.TypedContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

}
