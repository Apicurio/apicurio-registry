package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

import static io.javaoperatorsdk.operator.processing.event.ResourceID.fromResource;

public class CRContext {

    private static final Map<ResourceID, CRContext> contexts = new HashMap<>();

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
