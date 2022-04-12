package io.apicurio.registry.systemtest.operator;

import io.apicurio.registry.systemtest.framework.LoggerUtils;
import io.apicurio.registry.systemtest.operator.types.OperatorType;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import org.slf4j.Logger;

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
        operatorType.install();

        if(waitForReady) {
            assertTrue(waitOperatorReady(operatorType), String.format("Not ready: {}", operatorType.getDeployment().getMetadata().getName()));
        }
    }

    public void uninstallOperator(OperatorType operatorType, boolean waitForRemoved) {
        operatorType.uninstall();

        if(waitForRemoved) {
            assertTrue(waitOperatorRemoved(operatorType), String.format("Not removed: {}", operatorType.getDeployment().getMetadata().getName()));
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
            operatorManagerLogger.info("Operator failed condition check"); // : {}", resourceToString(res));
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
            operatorManagerLogger.info("Operator failed condition check"); // : {}", resourceToString(res));
        }

        return pass;
    }
}
