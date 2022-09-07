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

import io.apicurio.tests.common.Constants;
import io.apicurio.tests.selenium.resources.WebItem;
import io.apicurio.tests.ui.pages.BasePage;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class SeleniumProvider {

    protected static final Logger log = LoggerFactory.getLogger(SeleniumProvider.class);
    private static SeleniumProvider instance;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss:SSS");
    private WebDriver driver;
    private WebDriverWait driverWait;
    private Map<Date, File> browserScreenshots = new HashMap<>();

    private String uiUrl;

    private SeleniumProvider() {
    }

    public static synchronized SeleniumProvider getInstance() {
        if (instance == null) {
            instance = new SeleniumProvider();
        }
        return instance;
    }

    public void setupDriver(WebDriver driver) {
        this.driver = driver;
        this.driver.manage().window().setPosition(new Point(0, 0));
        this.driver.manage().window().setSize(new Dimension(1366, 768));
        driverWait = new WebDriverWait(driver, 10);
        browserScreenshots.clear();
    }

    public void setUiUrl(String uiUrl) {
        this.uiUrl = uiUrl;
    }

    public String getUiUrl() {
        return this.uiUrl;
    }

    public void tearDownDrivers() {
        log.info("Tear down selenium web drivers");
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception ex) {
                log.warn("Raise warning on quit: " + ex.getMessage());
            }
            log.info("Driver is closed");
            driver = null;
            driverWait = null;
            browserScreenshots.clear();
        }
    }

    public void onFailed(ExtensionContext extensionContext) {
        String getTestClassName = extensionContext.getTestClass().get().getName();
        String getTestMethodName = extensionContext.getTestMethod().get().getName();
        Path webConsolePath = getWebConsolePath(Constants.LOGS_DIR, getTestClassName, getTestMethodName);
        saveBrowserLog(webConsolePath);
//        collect logs(webConsolePath);
        saveScreenShots(webConsolePath, getTestClassName, getTestMethodName);

    }

    private void saveBrowserLog(Path path) {
        try {
            log.info("Saving browser console log...");
            Files.createDirectories(path);
            File consoleLog = new File(path.toString(), "browser_console.log");
            StringBuilder logEntries = formatedBrowserLogs();
            Files.write(Paths.get(consoleLog.getPath()), logEntries.toString().getBytes());
            log.info("Browser console log saved successfully : {}", consoleLog);
        } catch (Exception ex) {
            log.warn("Cannot save browser log: " + ex.getMessage());
        }
    }

    public void saveScreenShots(String className, String methodName) {
        Path webConsolePath = getWebConsolePath(Constants.LOGS_DIR, className, methodName);
        saveScreenShots(webConsolePath, className, methodName);
    }

    private void saveScreenShots(Path path, String className, String methodName) {
        try {
            takeScreenShot();
            Files.createDirectories(path);
            for (Date key : browserScreenshots.keySet()) {
                FileUtils.copyFile(browserScreenshots.get(key), new File(Paths.get(path.toString(),
                        String.format("%s.%s_%s.png", className, methodName, dateFormat.format(key))).toString()));
            }
            log.info("Screenshots stored");
        } catch (Exception ex) {
            log.warn("Cannot save screenshots: " + ex.getMessage());
        } finally {
            //tearDownDrivers();
        }
    }

    private LogEntries getBrowserLog() {
        return this.driver.manage().logs().get(LogType.BROWSER);
    }

    private StringBuilder formatedBrowserLogs() {
        StringBuilder logEntries = new StringBuilder();
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (LogEntry logEntry : getBrowserLog().getAll()) {
            logEntries.append(logEntry.getLevel()).append(": ")
                    .append(sdfDate.format(logEntry.getTimestamp())).append(": ")
                    .append(logEntry.getMessage()).append(System.lineSeparator());
        }
        return logEntries;
    }


    public WebDriver getDriver() {
        return this.driver;
    }

    public WebDriverWait getDriverWait() {
        return driverWait;
    }

    public void takeScreenShot() {
        try {
            log.info("Taking screenshot");
            browserScreenshots.put(new Date(), ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE));
        } catch (Exception ex) {
            log.warn("Cannot take screenshot: {}", ex.getMessage());
        }
    }

    public void takeScreenShot(String path) {
        try {
            log.warn("Taking screenshot");
            File file = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(file, new File(path));
        } catch (Exception ex) {
            log.warn("Cannot take screenshot: {}", ex.getMessage());
        }
    }

    public void clearScreenShots() {
        if (browserScreenshots != null) {
            browserScreenshots.clear();
            log.info("Screenshots cleared");
        }
    }

    public void clickOnItem(WebElement element) {
        clickOnItem(element, null);
    }

    public void clickOnItem(WebElement element, String textToLog) {
        takeScreenShot();
        assertNotNull(element, "Element to click is null");
        logCheckboxValue(element);
        log.info("Click on element: {}", textToLog == null ? element.getText() : textToLog);
        element.click();
        takeScreenShot();
    }

    public void clearInput(WebElement element) {
        element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        element.sendKeys(Keys.BACK_SPACE);
        log.info("Cleared input");
    }


    public void fillInputItem(WebElement element, String text) {
        takeScreenShot();
        assertNotNull(element, "Element to fill is null");
        clearInput(element);
        element.sendKeys(text);
        log.info("Filled input with text: " + (text.length() > 50 ? text.substring(0, 50) + "..." : text));
        takeScreenShot();
    }

    public void pressEnter(WebElement element) {
        takeScreenShot();
        assertNotNull(element, "Selenium provider failed, element is null");
        element.sendKeys(Keys.RETURN);
        log.info("Enter pressed");
        takeScreenShot();
    }

    public void refreshPage() {
        takeScreenShot();
        log.info("Web page is going to be refreshed");
        driver.navigate().refresh();
        log.info("Web page successfully refreshed");
        takeScreenShot();
    }

    private <T> T getElement(Supplier<T> webElement, int attempts, int count) throws Exception {
        T result = null;
        int i = 0;
        while (++i <= attempts) {
            try {
                result = webElement.get();
                if (result == null) {
                    log.warn("Element was not found, go to next iteration: {}", i);
                } else if (result instanceof WebElement) {
                    if (((WebElement) result).isEnabled()) {
                        break;
                    }
                    log.warn("Element was found, but it is not enabled, go to next iteration: {}", i);
                } else if (result instanceof List) {
                    if (((List<?>) result).size() == count) {
                        break;
                    }
                    log.warn("Elements were not found, go to next iteration: {}", i);
                }
            } catch (Exception ex) {
                log.warn("Element was not found, go to next iteration: {}", i);
            }
            Thread.sleep(1000);
        }
        return result;
    }

    public WebElement getInputByName(String inputName) {
        return this.getDriver().findElement(By.cssSelector(String.format("input[name='%s']", inputName)));
    }

    public WebElement getWebElement(Supplier<WebElement> webElement) throws Exception {
        return getElement(webElement, 60, 0);
    }

    public WebElement getWebElement(Supplier<WebElement> webElement, int attempts) throws Exception {
        return getElement(webElement, attempts, 0);
    }

    public List<WebElement> getWebElements(Supplier<List<WebElement>> webElements, int count) throws Exception {
        return getElement(webElements, 60, count);
    }

    public <T extends WebItem> T waitUntilItemPresent(int timeInSeconds, Supplier<T> item) throws Exception {
        return waitUntilItem(timeInSeconds, item, true);
    }

    public void waitUntilItemClickableByDataId(String dataId) {
        getDriverWait().withTimeout(Duration.ofSeconds(5)).until(ExpectedConditions.elementToBeClickable(BasePage.byDataTestId(dataId)));
    }

    public void waitUntilItemNotPresent(int timeInSeconds, Supplier<WebItem> item) throws Exception {
        waitUntilItem(timeInSeconds, item, false);
    }

    private Path getWebConsolePath(Path target, String className, String methodName) {
        String webconsoleFolder = "selenium_tests";
        return target.resolve(
                Paths.get(
                        webconsoleFolder,
                        className,
                        methodName));
    }

    private <T extends WebItem> T waitUntilItem(int timeInSeconds, Supplier<T> item, boolean present) throws Exception {
        log.info("Waiting for element {} present", present ? "to be" : "not to be");
        int attempts = 0;
        T result = null;
        while (attempts++ < timeInSeconds) {
            if (present) {
                try {
                    result = item.get();
                    if (result != null) {
                        break;
                    }
                } catch (Exception ignored) {
                } finally {
                    log.info("Element not present, go to next iteration: " + attempts);
                }
            } else {
                try {
                    if (item.get() == null) {
                        break;
                    }
                } catch (Exception ignored) {
                } finally {
                    log.info("Element still present, go to next iteration: " + attempts);
                }
            }
            Thread.sleep(1000);
        }
        log.info("End of waiting");
        return result;
    }

    public void waitUntilPropertyPresent(int timeoutInSeconds, int expectedValue, Supplier<Integer> item) throws
            Exception {
        log.info("Waiting until data will be present");
        int attempts = 0;
        Integer actual = null;
        while (attempts < timeoutInSeconds) {
            actual = item.get();
            if (expectedValue == actual)
                break;
            Thread.sleep(1000);
            attempts++;
        }
        log.info("End of waiting");
        assertEquals(expectedValue, actual, String.format("Property does not have expected value %d after timeout %ds.", expectedValue, timeoutInSeconds));
    }

    //================================================================================================
    //==================================== Checkbox methods ==========================================
    //================================================================================================

    public void setValueOnCheckboxRequestedPermissions(WebElement element, boolean check) {
        if (getCheckboxValue(element) != check) {
            clickOnItem(element);
        } else {
            log.info("Checkbox already {}", check ? "checked" : "unchecked");
        }
    }

    public boolean getCheckboxValue(WebElement element) {
        if (isCheckbox(element)) {
            return Boolean.parseBoolean(element.getAttribute("checked"));
        }
        throw new IllegalStateException("Requested element is not of type 'checkbox'");
    }

    private boolean isCheckbox(WebElement element) {
        String type = element.getAttribute("type");
        return type != null && type.equals("checkbox");
    }

    private void logCheckboxValue(WebElement element) {
        if (isCheckbox(element)) {
            log.info("Checkbox value before click is checked='{}'", element.getAttribute("checked"));
        }
    }

}