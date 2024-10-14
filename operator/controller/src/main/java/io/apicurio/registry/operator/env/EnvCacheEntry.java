package io.apicurio.registry.operator.env;

import io.fabric8.kubernetes.api.model.EnvVar;
import lombok.*;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Builder
@AllArgsConstructor(access = PRIVATE)
@Getter
@EqualsAndHashCode
@ToString
public class EnvCacheEntry {

    private final EnvVar var;

    private final EnvCachePriority priority;

    private final List<String> dependencies;
}
