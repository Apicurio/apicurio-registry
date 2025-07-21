package io.apicurio.registry.operator.testutils;

import io.apicurio.registry.utils.Cell;
import io.apicurio.registry.utils.Functional.FunctionEx;
import io.apicurio.registry.utils.Functional.RunnableEx;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static io.apicurio.registry.operator.it.ITBase.LONG_DURATION;
import static io.apicurio.registry.operator.it.ITBase.SHORT_DURATION;
import static io.apicurio.registry.utils.Cell.cell;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;

public class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static Random RANDOM = new Random();

    public static <T, X extends Exception> T withRetries(FunctionEx<T, X> action, Runnable orElse, int retries) {
        if (retries > LONG_DURATION.toSeconds()) {
            throw new IllegalArgumentException("Max " + LONG_DURATION.toSeconds() + " retries are supported.");
        }
        var rc = cell(retries);
        Cell<T> rval = cell();
        await().atMost(LONG_DURATION).pollDelay(ZERO).pollInterval(ofSeconds(1)).until(() -> {
            try {
                rval.set(action.run());
                return true;
            } catch (Exception | AssertionError ex) {
                if (rc.get() != 0) {
                    log.debug("Retrying:", ex);
                    orElse.run();
                    rc.map(r -> r - 1);
                    return false;
                } else {
                    throw ex;
                }
            }
        });
        return rval.get();
    }

    public static <T, X extends Exception> T withRetries(FunctionEx<T, X> action, int retries) {
        // @formatter:off
        return withRetries(action, () -> {}, retries);
        // @formatter:on
    }

    public static <X extends Exception> void withRetries(RunnableEx<X> action, int retries) {
        // @formatter:off
        withRetries(() -> { action.run(); return null; }, () -> {}, retries);
        // @formatter:on
    }

    /**
     * Update a Kubernetes resource, and retry if the update fails because the object has been modified on the server.
     *
     * @param action Reentrant function that updates a resource.
     */
    public static void updateWithRetries(Runnable action) {
        await().atMost(SHORT_DURATION).until(() -> {
            try {
                action.run();
                return true;
            } catch (KubernetesClientException ex) {
                if (ex.getMessage().contains("the object has been modified")) {
                    log.debug("Retrying:", ex);
                    return false;
                } else {
                    throw ex;
                }
            }
        });
    }
}
