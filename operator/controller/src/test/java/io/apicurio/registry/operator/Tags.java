package io.apicurio.registry.operator;

/**
 * JUnit test tags for categorizing operator tests.
 * These tags can be used to selectively run test groups via Maven:
 * {@code mvn verify -Dgroups=smoke} or {@code mvn verify -DexcludedGroups=slow}
 */
public final class Tags {

    /** OLM-specific tests that require Operator Lifecycle Manager */
    public static final String OLM = "OLM";

    /** Basic smoke tests - fast, minimal dependencies */
    public static final String SMOKE = "smoke";

    /** Tests that require Kafka/Strimzi infrastructure */
    public static final String KAFKA = "kafka";

    /** Tests that require Keycloak for authentication */
    public static final String AUTH = "auth";

    /** Tests that require a database (PostgreSQL, MySQL) */
    public static final String DATABASE = "database";

    /** Feature-specific tests (ingress, TLS, env vars, etc.) */
    public static final String FEATURE = "feature";

    /** Slow tests that take significant time */
    public static final String SLOW = "slow";

    private Tags() {
    }
}
