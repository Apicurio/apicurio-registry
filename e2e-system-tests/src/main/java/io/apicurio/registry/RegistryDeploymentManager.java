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

package io.apicurio.registry;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistryDeploymentManager implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback, ExtensionContext.Store.CloseableResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryDeploymentManager.class);

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        LOGGER.info("Test suite started ##################################################");
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        LOGGER.info("Individual Test started ##################################################");
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        LOGGER.info("Test suite ended ##################################################");
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        LOGGER.info("Individual Test executed ##################################################");

    }
    @Override
    public void close() throws Throwable {
        LOGGER.info("Closing test resources ##################################################3");
    }
}
