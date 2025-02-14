package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

import static io.javaoperatorsdk.operator.processing.event.ResourceID.fromResource;
import static java.lang.Long.parseLong;
import static java.util.Comparator.comparingLong;

public class LabelDiscriminator<R extends HasMetadata>
        implements ResourceDiscriminator<R, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(LabelDiscriminator.class);

    private final LabelSupplier labelSupplier;

    public LabelDiscriminator(LabelSupplier labelSupplier) {
        this.labelSupplier = labelSupplier;
    }

    public LabelDiscriminator(Map<String, String> labels) {
        this(_ignored -> labels);
    }

    @Override
    public Optional<R> distinguish(Class<R> resource, ApicurioRegistry3 primary,
                                   Context<ApicurioRegistry3> context) {

        var labels = labelSupplier.supply(primary);

        var filtered = context.getSecondaryResources(resource).stream()
                .filter(r -> {
                    for (var label : labels.entrySet()) {
                        var v = r.getMetadata().getLabels().get(label.getKey());
                        if (v == null || !v.equals(label.getValue())) {
                            return false;
                        }
                    }
                    return true;
                }).toList();

        switch (filtered.size()) {
            case 0 -> {
                return Optional.empty();
            }
            case 1 -> {
                return Optional.of(filtered.get(0));
            }
            default -> {
                var msg = "Expected at most one %s resource with labels %s but got more:\n%s"
                        .formatted(resource.getSimpleName(), labels, filtered.stream().map(ResourceID::fromResource).toList());
                if (filtered.stream().allMatch(r -> fromResource(filtered.get(0)).equals(fromResource(r)))) {
                    log.warn(msg);
                    return filtered.stream().max(comparingLong(r -> parseLong(r.getMetadata().getResourceVersion())));
                } else {
                    throw new OperatorException(msg);
                }
            }
        }
    }

    @FunctionalInterface
    public interface LabelSupplier {

        Map<String, String> supply(ApicurioRegistry3 primary);
    }
}
