package io.apicurio.registry.systemtest.operator;

import io.apicurio.registry.systemtest.framework.LoggerUtils;
import io.apicurio.registry.systemtest.operator.types.OperatorType;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OperatorManager {
    private static final Logger operatorManagerLogger = LoggerUtils.getLogger();

    private static OperatorManager instance;

    public static synchronized OperatorManager getInstance() {
        if (instance == null) {
            instance = new OperatorManager();
        }

        return instance;
    }

    public void installOperator(OperatorType operatorType, boolean waitForReady) {
        if(Kubernetes.getClient().namespaces().withName(operatorType.getNamespaceName()).get() == null) {
            operatorManagerLogger.info("Creating new namespace {} for operator {} with name {}...", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());

            Kubernetes.getClient().namespaces().create(new NamespaceBuilder()
                    .withNewMetadata()
                        .withName(operatorType.getNamespaceName())
                    .endMetadata()
                    .build()
            );

            Kubernetes.getClient().namespaces().withName(operatorType.getNamespaceName()).waitUntilReady(1, TimeUnit.MINUTES);

            operatorManagerLogger.info("New namespace {} for operator {} with name {} created.", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());
        } else {
            operatorManagerLogger.info("Namespace {} for operator {} with name {} already exists.", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());
        }

        operatorManagerLogger.info("Installing operator {} with name {} in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());

        operatorType.install();

        operatorManagerLogger.info("Operator {} with name {} installed in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());

        if(waitForReady) {
            operatorManagerLogger.info("Waiting for operator {} with name {} to be ready in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());

            assertTrue(waitOperatorReady(operatorType), MessageFormat.format("Timed out waiting for operator {} with name {} to be ready in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName()));

            if(operatorType.isReady()) {
                operatorManagerLogger.info("Operator {} with name {} is ready in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());
            }
        } else {
            operatorManagerLogger.info("Do not wait for operator {} with name {} to be ready in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());
        }
    }

    public void uninstallOperator(OperatorType operatorType, boolean waitForRemoved) {
        operatorManagerLogger.info("Uninstalling operator {} with name {} in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());

        operatorType.uninstall();

        if(waitForRemoved) {
            operatorManagerLogger.info("Waiting for operator {} with name {} to be uninstalled in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());

            assertTrue(waitOperatorRemoved(operatorType), MessageFormat.format("Timed out waiting for operator {} with name {} to be uninstalled in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName()));

            if(operatorType.doesNotExist()) {
                operatorManagerLogger.info("Operator {} with name {} uninstalled in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());
            }
        }  else {
            operatorManagerLogger.info("Do not wait for operator {} with name {} to be uninstalled in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());
        }

        if(Kubernetes.getClient().namespaces().withName(operatorType.getNamespaceName()).get() == null) {
            operatorManagerLogger.info("Namespace {} for operator {} with name {} already removed.", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());
        } else {
            operatorManagerLogger.info("Removing namespace {} for operator {} with name {}...", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());

            Kubernetes.getClient().namespaces().delete(Kubernetes.getClient().namespaces().withName(operatorType.getNamespaceName()).get());

            if(waitNamespaceRemoved(operatorType)) {
                operatorManagerLogger.info("Namespace {} for operator {} with name {} removed.", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());
            }
        }
    }

    public boolean waitNamespaceRemoved(OperatorType operatorType) {
        return waitNamespaceRemoved(operatorType, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public  boolean waitNamespaceRemoved(OperatorType operatorType, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (Kubernetes.getClient().namespaces().withName(operatorType.getNamespaceName()).get() == null) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        boolean pass = Kubernetes.getClient().namespaces().withName(operatorType.getNamespaceName()).get() == null;

        if (!pass) {
            operatorManagerLogger.info("Namespace {} for operator {} with name {} failed removal check.", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());
        }

        return pass;
    }

    public boolean waitOperatorReady(OperatorType operatorType) {
        return waitOperatorReady(operatorType, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public boolean waitOperatorReady(OperatorType operatorType, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (operatorType.isReady()) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        boolean pass = operatorType.isReady();

        if (!pass) {
            operatorManagerLogger.info("Operator {} with name {} in namespace {} failed readiness check.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());
        }

        return pass;
    }

    public boolean waitOperatorRemoved(OperatorType operatorType) {
        return waitOperatorRemoved(operatorType, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public boolean waitOperatorRemoved(OperatorType operatorType, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (operatorType.doesNotExist()) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        boolean pass = operatorType.doesNotExist();

        if (!pass) {
            operatorManagerLogger.info("Operator {} with name {} in namespace {} failed removal check.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());
        }

        return pass;
    }
}
