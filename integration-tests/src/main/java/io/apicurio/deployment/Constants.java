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

package io.apicurio.deployment;

import java.util.Optional;

public class Constants {

    /**
     * Registry image placeholder
     */
    static final String REGISTRY_IMAGE = "registry-image";

    /**
     * Tag for migration tests, the suite will deploy two registries and perform data migration between the two
     */
    static final String MULTITENANCY = "multitenancy";

    /**
     * Tag for auth tests profile.
     */
    static final String AUTH = "auth";

    /**
     * Tag for sql db upgrade tests profile.
     */
    static final String KAFKA_SQL = "kafkasqlit";

    /**
     * Tag for sql db upgrade tests profile.
     */
    static final String SQL = "sqlit";

    /**
     * Tag for ui tests profile.
     */
    static final String UI = "ui";


    public static final String TEST_PROFILE =
            Optional.ofNullable(System.getProperty("groups"))
                    .orElse("");
}
