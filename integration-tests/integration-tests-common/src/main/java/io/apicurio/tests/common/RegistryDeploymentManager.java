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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.utils.RegistryUtils;

public class RegistryDeploymentManager implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback, ExtensionContext.Store.CloseableResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryDeploymentManager.class);

    private static boolean suiteStarted = false;

    private static RegistryFacade registry = RegistryFacade.getInstance();

    void startRegistryIfNeeded(ExtensionContext context) throws Exception {
        if (!TestUtils.isExternalRegistry() && !registry.isRunning()) {
            LOGGER.info("Starting registry");
            try {
                registry.start();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } else {
            LOGGER.info("Going to use already running registries on {}", TestUtils.getRegistryV1ApiUrl());
        }
        if (!TestUtils.isExternalRegistry()) {
            try {
                registry.waitForRegistryReady();
            } catch (Exception e) {
                try {
                    Path logsPath = RegistryUtils.getLogsPath(context.getRequiredTestClass(), context.getDisplayName());
                    registry.stopAndCollectLogs(logsPath);
                } catch (IOException e1) {
                    e.addSuppressed(e1);
                }
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!suiteStarted) {
            suiteStarted = true;
            // The following line registers a callback hook when the root test context is shut down
            context.getRoot().getStore(Namespace.GLOBAL).put(UUID.randomUUID().toString(), this);
        }

        startRegistryIfNeeded(context);
        LOGGER.info("Registry app is running on {}", TestUtils.getRegistryV2ApiUrl());
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        startRegistryIfNeeded(context);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (context.getExecutionException().isPresent()) {
            LOGGER.error("Test failed with error:", context.getExecutionException().get());
        }
        if (!TestUtils.isExternalRegistry() && context.getExecutionException().isPresent()) {
            Path logsPath = RegistryUtils.getLogsPath(context.getRequiredTestClass(), context.getDisplayName());
            registry.stopAndCollectLogs(logsPath);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // do nothing because we want to start registry one time for all test suite
    }

    @Override
    public void close() throws Throwable {
        if (!TestUtils.isExternalRegistry() && registry.isRunning()) {
            LOGGER.info("Tear down registry deployment");
            try {
                registry.stopAndCollectLogs(null);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

}
