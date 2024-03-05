package io.apicurio.deployment.k8s.bundle;

import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.*;

import java.util.Map;

@Builder
@Getter
@EqualsAndHashCode
@ToString
public class ResourceDescriptor {

    private Class<? extends HasMetadata> type;

    private int count;

    @Singular
    private Map<String, String> labels;
}
