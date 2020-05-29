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
package io.apicurio.tests.selenium;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BrowserWebDriverContainer;

import io.apicurio.registry.utils.tests.TestUtils;

public class SeleniumChromeExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback, BeforeAllCallback, AfterAllCallback {

    protected static final Logger LOGGER = LoggerFactory.getLogger(SeleniumChromeExtension.class);

    private boolean isFullClass = false;
    private BrowserWebDriverContainer chrome;
    private boolean deployed = false;

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        deleteChrome();
        isFullClass = false;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        deployChrome();
        isFullClass = true;
    }

    @Override
    public void afterTestExecution(ExtensionContext extensionContext) throws Exception {
        if (extensionContext.getExecutionException().isPresent()) {
            SeleniumProvider.getInstance().onFailed(extensionContext);
        }
        if (extensionContext.getExecutionException().isPresent() || !isFullClass) {
            deleteChrome();
        }
    }

    @Override
    public void beforeTestExecution(ExtensionContext extensionContext) throws Exception {
        if (!deployed) {
            deployChrome();
        } else {
            SeleniumProvider.getInstance().clearScreenShots();
        }
    }

    private void deployChrome() {
        LOGGER.info("Deploying chrome browser");
        if (!TestUtils.isExternalRegistry()) {
            Testcontainers.exposeHostPorts(TestUtils.getRegistryPort());
        }
        chrome = new BrowserWebDriverContainer()
                .withCapabilities(new ChromeOptions());
        chrome.start();
        SeleniumProvider.getInstance().setupDriver(chrome.getWebDriver());
        SeleniumProvider.getInstance().setUiUrl(TestUtils.getRegistryUIUrl().replace("localhost", "host.testcontainers.internal"));
        deployed = true;
    }

    private void deleteChrome() {
        SeleniumProvider.getInstance().tearDownDrivers();
        LOGGER.info("Stopping chrome browser");
        chrome.stop();
        deployed = false;
    }
}
