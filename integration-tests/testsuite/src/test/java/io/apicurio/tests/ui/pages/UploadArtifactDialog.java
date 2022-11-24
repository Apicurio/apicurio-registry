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
import org.openqa.selenium.WebElement;

import io.apicurio.tests.selenium.SeleniumProvider;

public class UploadArtifactDialog extends BasePage {

    public UploadArtifactDialog(SeleniumProvider selenium) {
        super(selenium);
    }

    private WebElement getUploadArtifactDialog() {
        return selenium.getDriver().findElement(By.xpath("//div[@aria-modal='true']"));
    }

    public WebElement getGroupIdInput() {
        return getUploadArtifactDialog().findElement(byDataTestId("form-group"));
    }

    public WebElement getArtifactIdInput() {
        return getUploadArtifactDialog().findElement(byDataTestId("form-id"));
    }

    public WebElement getArtifactTypeDropdownToggle() {
        return getUploadArtifactDialog().findElement(byDataTestId("form-type-toggle"));
    }

    public WebElement getArtifactTypeDropdownItem(String type) {
        String formType = "form-type-";
        if (type == null) {
            formType += "auto";
        } else {
            formType += type;
        }
        return getUploadArtifactDialog().findElement(byDataTestId(formType));
    }

    public WebElement getArtifactContentInput() {
        return getUploadArtifactDialog()
                .findElement(byDataTestId("form-upload"))
                .findElement(By.xpath("//textarea[@aria-label='File upload']"));
    }

    public WebElement getArtifactURL() {
        return getUploadArtifactDialog().findElement(byDataTestId("artifact-content-url-input"));
    }

    public WebElement getFromUrlTab() {
        return getUploadArtifactDialog().findElement(byDataTestId("tab-from-url"));
    }

    public WebElement getFetchButton() {
        return getUploadArtifactDialog().findElement(byDataTestId("artifact-content-url-fetch"));
    }

    public WebElement getUploadButton() {
        return getUploadArtifactDialog().findElement(byDataTestId("modal-btn-upload"));
    }

    public void fillGroupId(String groupId) {
        selenium.fillInputItem(this.getGroupIdInput(), groupId);
    }

    public void fillArtifactId(String artifactId) {
        selenium.fillInputItem(this.getArtifactIdInput(), artifactId);
    }

}
