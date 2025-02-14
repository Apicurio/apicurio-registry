package io.apicurio.registry.operator.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;

import static io.apicurio.registry.operator.resource.Labels.getSelectorLabels;

public class ComponentLabelDiscriminator<R extends HasMetadata>
        extends LabelDiscriminator<R> {

    public ComponentLabelDiscriminator(String component) {
        super(primary -> getSelectorLabels(primary, component));
    }
}
