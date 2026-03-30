package io.apicurio.tests.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Interface for keep global constants used across system tests.
 */
public interface Constants {
    long POLL_INTERVAL = Duration.ofSeconds(1).toMillis();
    long TIMEOUT_FOR_REGISTRY_START_UP = Duration.ofSeconds(25).toMillis();
    long TIMEOUT_FOR_REGISTRY_READY = Duration.ofSeconds(30).toMillis();
    long TIMEOUT_GLOBAL = Duration.ofSeconds(30).toMillis();

    /**
     * Tag for tests, which are testing basic functionality
     */
    String SMOKE = "smoke";
    /**
     * Tag for tests, which are working with the cluster (integration of kafka with registries) such as serdes
     * and converters
     */
    String SERDES = "serdes";
    /**
     * Tag for acceptance tests, less tests than smoke testing
     */
    String ACCEPTANCE = "acceptance";

    /**
     * Tag for migration tests, the suite will deploy two registries and perform data migration between the
     * two
     */
    String MIGRATION = "migration";

    /**
     * Tag for auth tests, the suite will deploy apicurio registry with keycloak and verify the api is secured
     */
    String AUTH = "auth";

    /**
     * Tag for kafkasql snapshotting tests, the test will be executed only when the storage variant is
     * kafkasql.
     */
    String KAFKA_SQL_SNAPSHOTTING = "kafkasql-snapshotting";

    /**
     * Tag for Debezium integration tests
     */
    String DEBEZIUM = "debezium";

    /**
     * Tag for Debezium integration tests
     */
    String DEBEZIUM_MYSQL = "debezium-mysql";

    /**
     * Tag for Debezium integration tests
     */
    String DEBEZIUM_SNAPSHOT = "debezium-snapshot";

    /**
     * Tag for Debezium mysql integration tests
     */
    String DEBEZIUM_MYSQL_SNAPSHOT = "debezium-mysql-snapshot";

    /**
     * Tag for kubernetesops tests, the test will be executed only when the storage variant is kubernetesops.
     */
    String KUBERNETES_OPS = "kubernetesops";

    /**
     * Tag for Iceberg REST Catalog integration tests
     */
    String ICEBERG = "iceberg";

    /**
     * Tag for search tests, requires Elasticsearch to be available and the search feature enabled
     */
    String SEARCH = "search";

    Path LOGS_DIR = Paths.get("target/logs/");

    /**
     * Env var name for mandating the testsuite to avoid using docker for running required infrastructure
     */
    public static final String NO_DOCKER_ENV_VAR = "NO_DOCKER";

    /**
     * Current CI environment running the testsuite
     */
    public static final String CURRENT_ENV = "CURRENT_ENV";
    public static final String CURRENT_ENV_MAS_REGEX = ".*mas.*";

}
