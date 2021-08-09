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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
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

    private void deployChrome() throws Exception {
        LOGGER.info("Deploying chrome browser");
        String uiUrl;
        WebDriver driver;
        if (TestUtils.isExternalRegistry()) {
            // we are supposing that if registry is deployed externally selenium will be as well
            driver = getRemoteChromeDriver();
            String registrySeleniumHost =  System.getenv().getOrDefault("REGISTRY_SELENIUM_HOST", TestUtils.getRegistryHost());
            String registrySeleniumPort = System.getenv().getOrDefault("REGISTRY_SELENIUM_PORT", Integer.toString(TestUtils.getRegistryPort()));
            uiUrl = String.format("http://%s:%s/ui", registrySeleniumHost, registrySeleniumPort);
        } else {
            Testcontainers.exposeHostPorts(TestUtils.getRegistryPort());
            uiUrl = TestUtils.getRegistryUIUrl().replace("localhost", "host.testcontainers.internal");
            chrome = new BrowserWebDriverContainer()
                .withCapabilities(buildChromeOptions());
            chrome.start();
            driver = chrome.getWebDriver();
        }
        SeleniumProvider.getInstance().setupDriver(driver);
        SeleniumProvider.getInstance().setUiUrl(uiUrl);
        deployed = true;
    }

    private void deleteChrome() {
        SeleniumProvider.getInstance().tearDownDrivers();
        LOGGER.info("Stopping chrome browser");
        if (!TestUtils.isExternalRegistry()) {
            chrome.stop();
        }
        deployed = false;
    }

    public static RemoteWebDriver getRemoteChromeDriver() throws Exception {
        String seleniumHost =  System.getenv().getOrDefault("SELENIUM_HOST", "localhost");
        String seleniumPort = System.getenv().getOrDefault("SELENIUM_PORT", "80");
        return getRemoteDriver(seleniumHost, seleniumPort, buildChromeOptions());
    }

    private static ChromeOptions buildChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.setAcceptInsecureCerts(true);
        options.addArguments("test-type", "--headless", "--no-sandbox", "--disable-dev-shm-usage", "--disable-extensions");
        return options;
    }

    private static RemoteWebDriver getRemoteDriver(String host, String port, Capabilities options) throws Exception {
        int attempts = 60;
        URL hubUrl = new URL(String.format("http://%s:%s/wd/hub", host, port));
        LOGGER.info("Using remote selenium " + hubUrl);
        for (int i = 0; i < attempts; i++) {
            try {
                testReachable(hubUrl);
                return new RemoteWebDriver(hubUrl, options);
            } catch (IOException e) {
                if (i == attempts - 1) {
                    LOGGER.warn("Cannot connect to hub", e);
                } else {
                    LOGGER.warn("Cannot connect to hub: {}", e.getMessage());
                }
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("Selenium webdriver cannot connect to selenium container");
    }

    private static void testReachable(URL url) throws IOException {
        LOGGER.info("Trying to connect to {}", url.toString());
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.getContent();
            LOGGER.info("Client is able to connect to the selenium hub");
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
