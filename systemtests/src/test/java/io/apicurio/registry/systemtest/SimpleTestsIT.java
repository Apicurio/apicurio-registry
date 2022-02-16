package io.apicurio.registry.systemtest;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SimpleTestsIT {
    private static Logger LOGGER = LoggerFactory.getLogger(SimpleTestsIT.class);
    @Test
    public void simpleTestIT() {
        LOGGER.info("First test log!");
        assertThat("123", is("123"));
    }
}
