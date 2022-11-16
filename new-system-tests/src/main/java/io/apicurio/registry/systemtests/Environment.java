package io.apicurio.registry.systemtests;

/**
 * Environment variables used in test suite and tests.
 */
public class Environment {
    /** Type of cluster for tests. */
    public static final String CLUSTER_TYPE = System.getenv().getOrDefault("CLUSTER_TYPE", "kubernetes");
}
