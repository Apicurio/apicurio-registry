package io.apicurio.registry.systemtest.operator;

import io.apicurio.registry.systemtest.framework.LoggerUtils;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.operator.types.OperatorType;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.time.Duration;

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
        operatorManagerLogger.info("Installing operator {} with name {} in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace());

        operatorType.install();

        operatorManagerLogger.info("Operator {} with name {} installed in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace());

        if(waitForReady) {
            operatorManagerLogger.info("Waiting for operator {} with name {} to be ready in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace());

            assertTrue(waitOperatorReady(operatorType), MessageFormat.format("Timed out waiting for operator {} with name {} to be ready in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace()));

            if(operatorType.isReady()) {
                operatorManagerLogger.info("Operator {} with name {} is ready in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace());
            }
        } else {
            operatorManagerLogger.info("Do not wait for operator {} with name {} to be ready in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace());
        }
    }

    public void uninstallOperator(OperatorType operatorType, boolean waitForRemoved) {
        operatorManagerLogger.info("Uninstalling operator {} with name {} in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace());

        operatorType.uninstall();

        if(waitForRemoved) {
            operatorManagerLogger.info("Waiting for operator {} with name {} to be uninstalled in namespace {}...", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace());

            assertTrue(waitOperatorRemoved(operatorType), MessageFormat.format("Timed out waiting for operator {} with name {} to be uninstalled in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace()));

            if(operatorType.doesNotExist()) {
                operatorManagerLogger.info("Operator {} with name {} uninstalled in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace());
            }
        }  else {
            operatorManagerLogger.info("Do not wait for operator {} with name {} to be uninstalled in namespace {}.", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace());
        }
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
            operatorManagerLogger.info("Operator {} with name {} in namespace {} failed readiness check.", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace());
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
            operatorManagerLogger.info("Operator {} with name {} in namespace {} failed removal check.", operatorType.getKind(), operatorType.getDeploymentName(), OperatorUtils.getOperatorNamespace());
        }

        return pass;
    }
}
