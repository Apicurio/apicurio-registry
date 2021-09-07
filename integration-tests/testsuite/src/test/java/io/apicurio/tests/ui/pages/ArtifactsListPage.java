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

import static org.junit.Assert.assertNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.tests.selenium.SeleniumProvider;
import io.apicurio.tests.selenium.resources.ArtifactListItem;

public class ArtifactsListPage extends BasePage {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ArtifactsListPage.class);

    private UploadArtifactDialog uploadArtifactDialog;

    public ArtifactsListPage(SeleniumProvider selenium) {
        super(selenium);
        this.uploadArtifactDialog = new UploadArtifactDialog(selenium);
    }

    public void verifyIsOpen() throws Exception {
        selenium.getDriverWait().withTimeout(Duration.ofSeconds(30)).until(ExpectedConditions.and(
                ExpectedConditions.urlContains("/ui/artifacts")));
        assertNotNull(selenium.getWebElement(() -> getArtifactsTab()));
    }

    public WebElement getArtifactsTab() {
        var tabs = selenium.getDriver().findElements(By.className("pf-c-tabs__item-text"));
        if (tabs == null) {
            return null;
        }
        return tabs.stream().filter(we -> we.getText().equals("Artifacts")).findFirst().orElse(null);
    }

    public WebElement getEmptyUploadArtifactOpenDialogButton() {
        try {
            return selenium.getDriver().findElement(byDataTestId("empty-btn-upload"));
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public WebElement getTopUploadArtifactOpenDialogButton() {
        return selenium.getDriver().findElement(byDataTestId("btn-header-upload-artifact"));
    }

    public WebElement getArtifactsListSortButton() {
        return selenium.getDriver().findElement(byDataTestId("toolbar-btn-sort"));
    }

    private List<WebElement> getArtifactListWebElements() throws Exception {
        try {
            selenium.takeScreenShot();
            return selenium.getDriver().findElement(By.className("artifact-list"))
                    .findElements(By.className("artifact-list-item"));
        } catch (NoSuchElementException e) {
            selenium.takeScreenShot();
            verifyIsOpen();
            return Collections.emptyList();
        }
    }

    public List<ArtifactListItem> getArtifactListItems() throws Exception {
        List<WebElement> elements = getArtifactListWebElements();
        LOGGER.info("Artifact list items {}", elements.size());
        List<ArtifactListItem> items = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            WebElement element = elements.get(i);
            ArtifactListItem item = new ArtifactListItem(element);
            LOGGER.info("Got artifact {}", item.toString());
            items.add(item);
        }
        return items;
    }

    private WebElement getViewArtifactLink(String groupId, String artifactId) throws Exception {
        return getArtifactListItems()
                .stream()
                .filter(item -> item.matches(groupId, artifactId))
                .findFirst()
                .map(item -> item.getViewArtifactLink())
                .orElseThrow(() -> new IllegalStateException("Artifact " + groupId + " , " + artifactId + " not found"));
    }

    //sub-pages
    public UploadArtifactDialog getUploadArtifactDialogPage() {
        return this.uploadArtifactDialog;
    }

    public ArtifactDetailsPage openArtifactDetailsPage(String groupId, String artifactId) throws Exception {
        selenium.clickOnItem(this.getViewArtifactLink(groupId, artifactId));
        return getCurrentArtifactDetailsPage();
    }

    public ArtifactDetailsPage getCurrentArtifactDetailsPage() {
        return new ArtifactDetailsPage(selenium);
    }


}
