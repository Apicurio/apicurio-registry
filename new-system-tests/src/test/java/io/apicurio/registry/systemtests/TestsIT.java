package io.apicurio.registry.systemtests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestsIT extends BaseTest {
    @Test
    public void testChild() {
        System.out.println("Run testChild test.");

        assertNotNull(client);
    }
}
