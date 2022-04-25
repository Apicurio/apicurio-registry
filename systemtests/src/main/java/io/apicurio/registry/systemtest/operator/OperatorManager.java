package io.apicurio.registry.systemtest.operator;

import io.apicurio.registry.systemtest.framework.LoggerUtils;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.operator.types.Operator;
import io.apicurio.registry.systemtest.operator.types.OperatorType;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OperatorManager {
    private static final Logger operatorManagerLogger = LoggerUtils.getLogger();

    private static OperatorManager instance;

    private static final Map<String, Stack<Runnable>> storedOperators = new LinkedHashMap<>();

    public static synchronized OperatorManager getInstance() {
        if (instance == null) {
            instance = new OperatorManager();
        }

        return instance;
    }

    public void installOperator(ExtensionContext testContext, OperatorType operatorType) {
        installOperator(operatorType, true);

        synchronized (this) {
            storedOperators.computeIfAbsent(testContext.getDisplayName(), k -> new Stack<>());
            storedOperators.get(testContext.getDisplayName()).push(() -> uninstallOperator(operatorType));
        }
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

            if(OperatorUtils.waitNamespaceReady(operatorType.getNamespaceName())) {
                operatorManagerLogger.info("New namespace {} for operator {} with name {} is created and ready.", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());

                // Set flag that namespace for operator was created and did not exist before
                ((Operator) operatorType).setNamespaceCreated(true);
            }
        } else {
            operatorManagerLogger.info("Namespace {} for operator {} with name {} already exists.", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());
        }

        operatorManagerLogger.info("Installing operator {} with name {} in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());

        operatorType.install();

        operatorManagerLogger.info("Operator {} with name {} installed in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());

        if(waitForReady) {
            operatorManagerLogger.info("Waiting for operator {} with name {} to be ready in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());

            assertTrue(waitOperatorReady(operatorType), MessageFormat.format("Timed out waiting for operator {0} with name {1} to be ready in namespace {2}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName()));

            if(operatorType.isReady()) {
                operatorManagerLogger.info("Operator {} with name {} is ready in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());
            }
        } else {
            operatorManagerLogger.info("Do not wait for operator {} with name {} to be ready in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());
        }
    }

    public void uninstallOperator(OperatorType operatorType) {
        uninstallOperator(operatorType, true);
    }

    public void uninstallOperator(OperatorType operatorType, boolean waitForRemoved) {
        operatorManagerLogger.info("Uninstalling operator {} with name {} in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());

        operatorType.uninstall();

        if(waitForRemoved) {
            operatorManagerLogger.info("Waiting for operator {} with name {} to be uninstalled in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());

            assertTrue(waitOperatorRemoved(operatorType), MessageFormat.format("Timed out waiting for operator {0} with name {1} to be uninstalled in namespace {2}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName()));

            if(operatorType.doesNotExist()) {
                operatorManagerLogger.info("Operator {} with name {} uninstalled in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());
            }
        }  else {
            operatorManagerLogger.info("Do not wait for operator {} with name {} to be uninstalled in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName());
        }

        // Check flag if operator namespace was created and did not exist before
        if(((Operator) operatorType).getNamespaceCreated()) {
            // Delete operator namespace when created by test

            if (Kubernetes.getClient().namespaces().withName(operatorType.getNamespaceName()).get() == null) {
                operatorManagerLogger.info("Namespace {} for operator {} with name {} already removed.", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());
            } else {
                operatorManagerLogger.info("Removing namespace {} for operator {} with name {}...", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());

                Kubernetes.getClient().namespaces().delete(Kubernetes.getClient().namespaces().withName(operatorType.getNamespaceName()).get());

                if (OperatorUtils.waitNamespaceRemoved(operatorType.getNamespaceName())) {
                    operatorManagerLogger.info("Namespace {} for operator {} with name {} removed.", operatorType.getNamespaceName(), operatorType.getKind(), operatorType.getDeploymentName());
                }
            }
        }
    }

    public void uninstallOperators(ExtensionContext testContext) {
        operatorManagerLogger.info("----------------------------------------------");
        operatorManagerLogger.info("Going to uninstall all operators.");
        operatorManagerLogger.info("----------------------------------------------");
        operatorManagerLogger.info("Operators key: {}", testContext.getDisplayName());
        if (!storedOperators.containsKey(testContext.getDisplayName()) || storedOperators.get(testContext.getDisplayName()).isEmpty()) {
            operatorManagerLogger.info("Nothing to uninstall.");
        }
        while (!storedOperators.get(testContext.getDisplayName()).isEmpty()) {
            storedOperators.get(testContext.getDisplayName()).pop().run();
        }
        operatorManagerLogger.info("----------------------------------------------");
        operatorManagerLogger.info("");
        storedOperators.remove(testContext.getDisplayName());
    }

    public boolean waitOperatorReady(OperatorType operatorType) {
        return waitOperatorReady(operatorType, TimeoutBudget.ofDuration(Duration.ofMinutes(7)));
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
