package io.apicurio.registry.systemtest.operator.types;

import io.fabric8.kubernetes.api.model.apps.Deployment;

public class ApicurioRegistryOLMOperatorType extends Operator implements OperatorType {
    public ApicurioRegistryOLMOperatorType() {
        super("/tmp/install.yaml");
    }

    public ApicurioRegistryOLMOperatorType(String source) {
        super(source);
    }

    @Override
    public String getKind() {
        return OperatorKind.APICURIO_REGISTRY_OLM_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return null;
    }

    @Override
    public String getDeploymentName() {
        return null;
    }

    @Override
    public Deployment getDeployment() {
        return null;
    }

    @Override
    public void install() {

    }

    @Override
    public void uninstall() {

    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public boolean doesNotExist() {
        return false;
    }
}
