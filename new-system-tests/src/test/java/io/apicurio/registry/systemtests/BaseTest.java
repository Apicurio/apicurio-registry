package io.apicurio.registry.systemtests;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KubernetesTest
public class BaseTest {
    protected static KubernetesClient client;

    @BeforeAll
    public static void beforeAll() {
        System.out.println("Run before all function.");

        assertNotNull(client);
    }

    @AfterAll
    public static void afterAll() {
        System.out.println("Run after all function.");

        assertNotNull(client);
    }

    @BeforeEach
    public void beforeEach() {
        System.out.println("Run before each function.");

        assertNotNull(client);
    }

    @AfterEach
    public void afterEach() {
        System.out.println("Run after each function.");

        assertNotNull(client);
    }

    @Test
    public void testParent() {
        System.out.println("Run testParent test.");

        assertNotNull(client);
    }
}
