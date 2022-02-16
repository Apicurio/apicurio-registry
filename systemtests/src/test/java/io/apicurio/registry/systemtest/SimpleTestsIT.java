package io.apicurio.registry.systemtest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SimpleTestsIT {

    @Test
    public void simpleTestIT() {
        assertThat("123", is("123"));
    }
}
