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
    long TIMEOUT_FOR_REGISTRY_START_UP = Duration.ofSeconds(15).toMillis();
    long TIMEOUT_FOR_REGISTRY_READY = Duration.ofSeconds(25).toMillis();
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

    Path LOGS_DIR = Paths.get("target/logs/");
}
