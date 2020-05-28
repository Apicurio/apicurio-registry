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
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BrowserWebDriverContainer;

import io.apicurio.registry.utils.tests.TestUtils;

public class SeleniumChromeExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback, BeforeAllCallback, AfterAllCallback {

    private boolean isFullClass = false;
    private BrowserWebDriverContainer chrome;

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        SeleniumProvider.getInstance().tearDownDrivers();
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
        SeleniumProvider.getInstance().tearDownDrivers();
        if (!isFullClass) {
            deleteChrome();
        } else {
            deleteChrome();
            deployChrome();
        }
    }

    @Override
    public void beforeTestExecution(ExtensionContext extensionContext) throws Exception {
        if (!isFullClass) {
            deployChrome();
        }
        if (SeleniumProvider.getInstance().getDriver() == null) {
            SeleniumProvider.getInstance().setupDriver(chrome.getWebDriver());
            SeleniumProvider.getInstance().setUiUrl(TestUtils.getRegistryUIUrl().replace("localhost", "host.testcontainers.internal"));
        } else {
            SeleniumProvider.getInstance().clearScreenShots();
        }
    }

    private void deployChrome() {
        Testcontainers.exposeHostPorts(TestUtils.getRegistryPort());
        chrome = new BrowserWebDriverContainer()
                .withCapabilities(new ChromeOptions());
        chrome.start();
    }

    private void deleteChrome() {
        chrome.stop();
    }
}
