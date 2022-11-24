package io.apicurio.registry.systemtests;

import io.fabric8.openshift.client.OpenShiftClient;
import org.junit.jupiter.api.Test;

/**
 * Parent class for future subclasses that will share tests.
 */
public abstract class TestBase {
    /**
     * @return {@link OpenShiftClient} instance used in tests.
     */
    protected abstract OpenShiftClient getClient();

    /**
     * We can add test(s) that will be shared between multiple subclasses in the future here.
     */
}
