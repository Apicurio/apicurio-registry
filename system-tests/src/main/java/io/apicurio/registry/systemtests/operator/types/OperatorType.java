package io.apicurio.registry.systemtests.operator.types;

import io.fabric8.kubernetes.api.model.apps.Deployment;

public interface OperatorType {
    OperatorKind getKind();

    String getNamespaceName();

    String getDeploymentName();

    Deployment getDeployment();

    void install() throws InterruptedException;

    void uninstall();

    boolean isReady();

    boolean doesNotExist();
}
