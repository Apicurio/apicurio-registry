package io.apicurio.registry.systemtest.operator.types;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.extension.ExtensionContext;

public interface OperatorType {
    OperatorKind getKind();

    String getNamespaceName();

    String getDeploymentName();

    Deployment getDeployment();

    void install(ExtensionContext testContext);

    void uninstall();

    boolean isReady();

    boolean doesNotExist();
}
