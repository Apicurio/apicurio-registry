package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;

import java.util.Map;
import java.util.Optional;

public class LabelDiscriminator<R extends HasMetadata>
        implements ResourceDiscriminator<R, ApicurioRegistry3> {

    private final Map<String, String> labels;

    public LabelDiscriminator(Map<String, String> labels) {
        this.labels = labels;
    }

    @Override
    public Optional<R> distinguish(Class<R> resource, ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context) {
        var resources = context.getSecondaryResources(resource);
        var filtered = resources.stream().filter(r -> {
            for (var label : labels.entrySet()) {
                var v = r.getMetadata().getLabels().get(label.getKey());
                if (v == null || !v.equals(label.getValue())) {
                    return false;
                }
            }
            return true;
        }).toList();
        if (filtered.size() > 1) {
            throw new OperatorException("Expected at most one " + resource.getSimpleName()
                    + " resource with labels " + labels + " but got more.");
        }
        return filtered.isEmpty() ? Optional.empty() : Optional.of(filtered.get(0));
    }
}
