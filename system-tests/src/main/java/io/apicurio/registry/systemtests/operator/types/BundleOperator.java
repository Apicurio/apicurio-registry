package io.apicurio.registry.systemtests.operator.types;

import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.List;

public abstract class BundleOperator extends Operator {
    private List<HasMetadata> resources;

    public BundleOperator(String source, String operatorNamespace) {
        super(source, operatorNamespace);
    }

    public List<HasMetadata> getResources() {
        return resources;
    }

    public void setResources(List<HasMetadata> resources) {
        this.resources = resources;
    }
}
