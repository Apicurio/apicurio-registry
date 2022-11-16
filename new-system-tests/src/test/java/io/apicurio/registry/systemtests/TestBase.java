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
     * Example of future test that can be shared with multiple subclasses.
     */
    @Test
    public void test1() {
        // Log information about current action
        System.out.println("### test1 test ###");
    }
}
