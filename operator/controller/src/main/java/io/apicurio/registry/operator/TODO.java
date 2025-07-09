package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.apicurio.registry.operator.resource.Labels.getSelectorLabels;
import static io.javaoperatorsdk.operator.processing.event.ResourceID.fromResource;
import static java.lang.Long.parseLong;
import static java.util.Comparator.comparingLong;

public class TODO {

    private static final Logger log = LoggerFactory.getLogger(TODO.class);

    public static <R extends HasMetadata> Optional<R> getSecondaryResource(Context<ApicurioRegistry3> context, ApicurioRegistry3 primary, ResourceKey<R> key) {
        var labels = getSelectorLabels(primary, key.getComponent());

        var resources = context.getSecondaryResources(key.getKlass())
                .stream()
                .filter(r -> r.getMetadata().getLabels().entrySet().containsAll(labels.entrySet()))
                .toList();

        return switch (resources.size()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(resources.get(0));
            default -> {
                var msg = "Expected at most one %s resource with labels %s but got more:\n%s"
                        .formatted(key.getKlass().getSimpleName(), labels, resources.stream().map(ResourceID::fromResource).toList());
                if (resources.stream().allMatch(r -> fromResource(resources.get(0)).equals(fromResource(r)))) {
                    log.debug(msg);
                    yield resources.stream().max(comparingLong(r -> parseLong(r.getMetadata().getResourceVersion())));
                } else {
                    throw new OperatorException(msg);
                }
            }
        };
    }
}
