/*
 * Copyright 2023 Red Hat
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

package io.apicurio.registry.deployment;

import java.util.Optional;

public class Constants {
    /**
     * Tag for tests, which are testing basic functionality
     */
    static String SMOKE = "smoke";
    /**
     * Tag for tests, which are working with the cluster (integration of kafka with registries) such as serdes and converters
     */
    static String SERDES = "serdes";
    /**
     * Tag for web ui tests
     */
    static String UI = "ui";

    /**
     * Tag for acceptance tests, less tests than smoke testing
     */
    static String ACCEPTANCE = "acceptance";

    /**
     * Tag for migration tests, the suite will deploy two registries and perform data migration between the two
     */
    static String MIGRATION = "migration";

    /**
     * Tag for auth tests, the suite will deploy apicurio registry with keycloak and verify the api is secured
     */
    static String AUTH = "auth";

    /**
     * Tag for sql storage db schema upgrade tests. Consists of one test that deploys an older version of the registry, populates the db, and then deploys the latest version of the registry.
     * Used to test the db schema upgrade process.
     */
    static String DB_UPGRADE = "dbupgrade";

    public static final String TEST_PROFILE =
            Optional.ofNullable(System.getProperty("groups"))
                    .orElse("");
}
