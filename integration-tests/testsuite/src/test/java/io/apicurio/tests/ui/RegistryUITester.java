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
package io.apicurio.tests.ui;

import static org.junit.Assert.assertNotNull;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.tests.selenium.SeleniumProvider;
import io.apicurio.tests.selenium.resources.ArtifactListItem;
import io.apicurio.tests.ui.pages.ArtifactDetailsPage;
import io.apicurio.tests.ui.pages.ArtifactsListPage;
import io.apicurio.tests.ui.pages.UploadArtifactDialog;

public class RegistryUITester {

    private static final Logger log = LoggerFactory.getLogger(RegistryUITester.class);

    private SeleniumProvider selenium;
    private String url;
    private ArtifactsListPage artifactsListPage;

    public RegistryUITester(SeleniumProvider selenium) {
        this.selenium = selenium;
        this.url = selenium.getUiUrl();
        this.artifactsListPage = new ArtifactsListPage(selenium);
    }

    public void openWebPage() throws Exception {
        log.info("Opening global console on url {}", url);
        selenium.getDriver().get(url);
        artifactsListPage.verifyIsOpen();
    }

    public String uploadArtifact(String groupId, String artifactId, ArtifactType type, String content) throws UnsupportedEncodingException {

        UploadArtifactDialog uploadDialog = openUploadArtifactDialog();

        if (groupId != null) {
            uploadDialog.fillGroupId(groupId);
        }

        if (artifactId != null) {
            uploadDialog.fillArtifactId(artifactId);
        }

        selenium.clickOnItem(uploadDialog.getArtifactTypeDropdownToggle());
        selenium.clickOnItem(uploadDialog.getArtifactTypeDropdownItem(type));

        selenium.fillInputItem(uploadDialog.getArtifactContentInput(), content);

        try {
            selenium.clickOnItem(uploadDialog.getUploadButton());
        } finally {
            selenium.takeScreenShot();
        }

        try {
            selenium.getDriverWait().withTimeout(Duration.ofSeconds(10)).until(
                    ExpectedConditions.urlContains("/versions/latest"));
            String[] slices = selenium.getDriver().getCurrentUrl().split("/");
            String aid = slices[slices.length - 3 ];
            return URLDecoder.decode(aid, StandardCharsets.UTF_8.name());
        } finally {
            selenium.takeScreenShot();
        }

    }

    public UploadArtifactDialog openUploadArtifactDialog() {
        selenium.clickOnItem(artifactsListPage.getUploadArtifactOpenDialogButton());
        return artifactsListPage.getUploadArtifactDialogPage();
    }

    public void goBackToArtifactsList() throws Exception {
        assertNotNull(selenium.getWebElement(() -> artifactsListPage.getUploadArtifactDialogPage().getLinkToArtifactsListPage()));

        selenium.clickOnItem(artifactsListPage.getUploadArtifactDialogPage().getLinkToArtifactsListPage());

        try {
            artifactsListPage.verifyIsOpen();
        } finally {
            selenium.takeScreenShot();
        }
    }

    public List<ArtifactListItem>  getArtifactsList() throws Exception {
        return artifactsListPage.getArtifactListItems();
    }

    public void deleteArtifact(String groupId, String artifactId) throws Exception {

        ArtifactDetailsPage detailsPage = artifactsListPage.openArtifactDetailsPage(groupId, artifactId);

        selenium.clickOnItem(detailsPage.getDeleteButton());
        selenium.clickOnItem(detailsPage.getDeleteButtonDeleteDialog());
        try {
            artifactsListPage.verifyIsOpen();
        } finally {
            selenium.takeScreenShot();
        }
    }

    public ArtifactsListPage getArtifactsListPage() {
        return this.artifactsListPage;
    }

}
