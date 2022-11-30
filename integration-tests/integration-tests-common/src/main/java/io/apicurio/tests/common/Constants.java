/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.tests.common;

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
     * Tag for tests, which are working with the cluster (integration of kafka with registries) such as serdes and converters
     */
    String SERDES = "serdes";
    /**
     * Tag for web ui tests
     */
    String UI = "ui";

    /**
     * Tag for acceptance tests, less tests than smoke testing
     */
    String ACCEPTANCE = "acceptance";

    /**
     * Tag for multitenancy tests, only storages that support multitenancy
     */
    String MULTITENANCY = "multitenancy";

    /**
     * Tag for clustered tests, the suite will deploy the registry as a cluster of 2 replicas
     */
    String CLUSTERED = "clustered";

    /**
     * Tag for migration tests, the suite will deploy two registries and perform data migration between the two
     */
    String MIGRATION = "migration";

    /**
     * Tag for auth tests, the suite will deploy apicurio registry with keycloak and verify the api is secured
     */
    String AUTH = "auth";

    /**
     * Tag for kafkasql tests, the test will be executed only when the storage variant is kafkasql.
     */
    String KAFKA_SQL = "kafkasqlit";

    /**
     * Tag for sql tests, the test will be executed only when the storage variant is sql.
     */
    String SQL = "sqlit";

    /**
     * Tag for sql storage db schema upgrade tests. Consists of one test that deploys an older version of the registry, populates the db, and then deploys the latest version of the registry.
     * Used to test the db schema upgrade process.
     */
    String DB_UPGRADE = "dbupgrade";

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

    /**
     * Env var used by the e2e testsuite to provide an already deployed kafka cluster in kubernetes in order to use it in the tests
     */
    public static final String TESTS_SHARED_KAFKA_ENV_VAR = "TESTS_SHARED_KAFKA";

}
