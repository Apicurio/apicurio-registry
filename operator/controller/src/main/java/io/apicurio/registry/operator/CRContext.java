package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.javaoperatorsdk.operator.processing.event.ResourceID.fromResource;

/**
 * WARNING: Resources inside the CR context must be thread-safe if they might be used by multiple dependent resources.
 */
public class CRContext {

    private static final Map<ResourceID, CRContext> contexts = new ConcurrentHashMap<>();

    public static CRContext getCRContext(ApicurioRegistry3 primary) {
        return contexts.computeIfAbsent(fromResource(primary), _ignored -> new CRContext());
    }

    public static void deleteCRContext(ApicurioRegistry3 primary) {
        contexts.remove(fromResource(primary));
    }

    // TODO: Use this for io.apicurio.registry.operator.status.StatusManager

    @Getter
    private final AnnotationManager appIngressAnnotations = new AnnotationManager();

    @Getter
    private final AnnotationManager uiIngressAnnotations = new AnnotationManager();
}
