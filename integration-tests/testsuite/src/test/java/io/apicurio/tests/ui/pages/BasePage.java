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
package io.apicurio.tests.ui.pages;

import org.openqa.selenium.By;

import io.apicurio.tests.selenium.SeleniumProvider;

public abstract class BasePage {

    protected SeleniumProvider selenium;

    public BasePage(SeleniumProvider selenium) {
        super();
        this.selenium = selenium;
    }

    public static By byDataTestId(String dataTestId) {
        return By.xpath(".//*[@data-testid='" + dataTestId + "']");
    }

    public static By byDataTestIdLike(String dataTestId) {
        return By.xpath(".//*[contains(@data-testid, '" + dataTestId + "')]");
    }

}
