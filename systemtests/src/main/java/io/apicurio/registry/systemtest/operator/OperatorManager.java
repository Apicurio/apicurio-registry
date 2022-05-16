package io.apicurio.registry.systemtest.operator;

import io.apicurio.registry.systemtest.framework.LoggerUtils;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.operator.types.Operator;
import io.apicurio.registry.systemtest.operator.types.OperatorType;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OperatorManager {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private static OperatorManager instance;
    private static final Map<String, Stack<Runnable>> STORED_OPERATORS = new LinkedHashMap<>();

    public static synchronized OperatorManager getInstance() {
        if (instance == null) {
            instance = new OperatorManager();
        }

        return instance;
    }

    private void createOperatorNamespace(OperatorType operatorType) {
        String namespace = operatorType.getNamespaceName();

        LOGGER.info("Creating new namespace {} for operator...", namespace);

        Kubernetes.createNamespace(namespace);

        if (OperatorUtils.waitNamespaceReady(namespace)) {
            LOGGER.info("Namespace {} for operator is created and ready.", namespace);

            // Set flag that namespace for operator was created and did not exist before
            ((Operator) operatorType).setNamespaceCreated(true);
        } else {
            LOGGER.error("Namespace {} for operator is not created and ready.", namespace);
        }
    }

    private void deleteOperatorNamespace(OperatorType operatorType) {
        String namespace = operatorType.getNamespaceName();

        if (Kubernetes.getNamespace(namespace) == null) {
            LOGGER.info("Namespace {} for operator already removed.", namespace);
        } else {
            LOGGER.info("Removing namespace {} for operator ...", namespace);

            Kubernetes.deleteNamespace(Kubernetes.getNamespace(namespace));

            if (OperatorUtils.waitNamespaceRemoved(namespace)) {
                LOGGER.info("Namespace {} for operator removed.", namespace);
            } else {
                LOGGER.error("Namespace {} for operator is not removed.", namespace);
            }
        }
    }

    public void installOperator(ExtensionContext testContext, OperatorType operatorType) {
        installOperator(testContext, operatorType, true);

        synchronized (this) {
            STORED_OPERATORS.computeIfAbsent(testContext.getDisplayName(), k -> new Stack<>());
            STORED_OPERATORS.get(testContext.getDisplayName()).push(() -> uninstallOperator(operatorType));
        }
    }

    public void installOperator(ExtensionContext testContext, OperatorType operatorType, boolean waitReady) {
        String kind = operatorType.getKind().toString();
        String name = operatorType.getDeploymentName();
        String namespace = operatorType.getNamespaceName();
        String operatorInfo = MessageFormat.format("{0} with name {1} in namespace {2}", kind, name, namespace);

        if (Kubernetes.getNamespace(namespace) == null) {
            createOperatorNamespace(operatorType);
        } else {
            LOGGER.info("Namespace {} for operator {} with name {} already exists.", namespace, kind, name);
        }

        LOGGER.info("Installing operator {}...", operatorInfo);

        operatorType.install(testContext);

        LOGGER.info("Operator {} installed.", operatorInfo);

        if (waitReady) {
            LOGGER.info("Waiting for operator {} to be ready...", operatorInfo);

            assertTrue(
                    waitOperatorReady(operatorType),
                    MessageFormat.format("Timed out waiting for operator {0} to be ready.", operatorInfo)
            );

            if (operatorType.isReady()) {
                LOGGER.info("Operator {} is ready.", operatorInfo);
            }
        } else {
            LOGGER.info("Do not wait for operator {} to be ready.", operatorInfo);
        }
    }

    public void uninstallOperator(OperatorType operatorType) {
        uninstallOperator(operatorType, true);
    }

    public void uninstallOperator(OperatorType operatorType, boolean waitRemoved) {
        String kind = operatorType.getKind().toString();
        String name = operatorType.getDeploymentName();
        String namespace = operatorType.getNamespaceName();
        String operatorInfo = MessageFormat.format("{0} with name {1} in namespace {2}", kind, name, namespace);

        LOGGER.info("Uninstalling operator {}...", operatorInfo);

        operatorType.uninstall();

        if (waitRemoved) {
            LOGGER.info("Waiting for operator {} to be uninstalled...", operatorInfo);

            assertTrue(
                    waitOperatorRemoved(operatorType),
                    MessageFormat.format("Timed out waiting for operator {0} to be uninstalled.", operatorInfo)
            );

            if (operatorType.doesNotExist()) {
                LOGGER.info("Operator {} uninstalled.", operatorInfo);
            }
        }  else {
            LOGGER.info("Do not wait for operator {} to be uninstalled.", operatorInfo);
        }

        // Check flag if operator namespace was created and did not exist before
        if (((Operator) operatorType).getNamespaceCreated()) {
            // Delete operator namespace when created by test
            deleteOperatorNamespace(operatorType);
        }
    }

    public void uninstallOperators(ExtensionContext testContext) {
        LOGGER.info("----------------------------------------------");
        LOGGER.info("Going to uninstall all operators.");
        LOGGER.info("----------------------------------------------");
        LOGGER.info("Operators key: {}", testContext.getDisplayName());

        if (
                !STORED_OPERATORS.containsKey(testContext.getDisplayName())
                || STORED_OPERATORS.get(testContext.getDisplayName()).isEmpty()
        ) {
            LOGGER.info("Nothing to uninstall.");
        } else {
            while (!STORED_OPERATORS.get(testContext.getDisplayName()).isEmpty()) {
                STORED_OPERATORS.get(testContext.getDisplayName()).pop().run();
            }
        }

        LOGGER.info("----------------------------------------------");
        LOGGER.info("");
        STORED_OPERATORS.remove(testContext.getDisplayName());
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

        if (!operatorType.isReady()) {
            LOGGER.info(
                    "Operator {} with name {} in namespace {} failed readiness check.",
                    operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName()
            );

            return false;
        }

        return true;
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

        if (!operatorType.doesNotExist()) {
            LOGGER.info(
                    "Operator {} with name {} in namespace {} failed removal check.",
                    operatorType.getKind(), operatorType.getDeploymentName(), operatorType.getNamespaceName()
            );

            return false;
        }

        return true;
    }
}
