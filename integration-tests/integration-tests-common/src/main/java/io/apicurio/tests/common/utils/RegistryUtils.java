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
package io.apicurio.tests.common.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import io.apicurio.tests.common.RegistryStorageType;

public class RegistryUtils {

    public static RegistryStorageType REGISTRY_STORAGE =
            Optional.ofNullable(System.getProperty("test.storage"))
                .map(RegistryStorageType::valueOf)
                .orElse(null);

    public static final String TEST_PROFILE =
            Optional.ofNullable(System.getProperty("groups"))
                .orElse("");

    public static final String DEPLOY_NATIVE_IMAGES =
            Optional.ofNullable(System.getProperty("testNative"))
                .orElse("");


    private RegistryUtils() {
        //utils class
    }

    public static Path getLogsPath(Class<?> testClass, String testName) {
        return Paths.get("target/logs/", REGISTRY_STORAGE.name(), testClass.getName(), testName);
    }

}