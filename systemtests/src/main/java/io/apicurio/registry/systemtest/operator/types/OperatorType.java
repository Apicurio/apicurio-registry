package io.apicurio.registry.systemtest.operator.types;

import io.fabric8.kubernetes.api.model.apps.Deployment;

public interface OperatorType {
    String getKind();

    String getNamespaceName();

    String getDeploymentName();

    Deployment getDeployment();

    void install();

    void uninstall();

    boolean isReady();

    boolean doesNotExist();
}
