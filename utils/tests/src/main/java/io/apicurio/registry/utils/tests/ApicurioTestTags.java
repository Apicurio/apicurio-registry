package io.apicurio.registry.utils.tests;

/**
 * JUnit test tags used to categorize app-module tests into CI shards (see
 * .github/workflows/verify-unit-tests.yaml and .github/scripts/verify-test-shards.py). Every
 * surefire-eligible test class in the app module must carry exactly one of the group tags below so that
 * it is claimed by exactly one shard. See issue #9302.
 */
public class ApicurioTestTags {

    /** Docker is required in the running machine to run this test. */
    public static final String DOCKER = "docker";

    /**
     * Test marked as slow. This usually means that this test uses a profile and therefore an application
     * restart is required.
     */
    public static final String SLOW = "slow";

    /** Authentication, authorization (RBAC), and plan/tenant limits tests. Maps to the app-auth shard. */
    public static final String AUTH = "auth";

    /** Transport-layer tests: TLS, custom headers, CORS. Maps to the app-transport shard. */
    public static final String TRANSPORT = "transport";

    /** Metrics and search tests. Maps to the app-metrics shard. */
    public static final String METRICS = "metrics";

}
