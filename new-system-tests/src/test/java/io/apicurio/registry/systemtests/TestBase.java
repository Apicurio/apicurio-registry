package io.apicurio.registry.systemtests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parent class for future subclasses that will share tests.
 */
public abstract class TestBase {
    /** {@link Logger} instance for logging in tests. */
    protected Logger logger = LoggerFactory.getLogger(getClass().getName());

    /**
     * We can add test(s) that will be shared between multiple subclasses in the future here.
     */
}
