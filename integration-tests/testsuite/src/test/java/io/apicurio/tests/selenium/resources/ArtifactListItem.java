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
package io.apicurio.tests.selenium.resources;

import static io.apicurio.tests.ui.pages.BasePage.byDataTestId;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class ArtifactListItem extends WebItem {

    private String groupId;
    private String artifactId;
    private String name;
    private String description;
    private String link;
    private WebElement viewArtifactLink;

    public ArtifactListItem(WebElement webItem) throws UnsupportedEncodingException {
        super(webItem);

        viewArtifactLink = webItem.findElement(byDataTestId("artifacts-lnk-view-1"));
        link = viewArtifactLink.getAttribute("href");
        String[] slices = link.split("/");

        name = viewArtifactLink.getText();

        // sclies href="/ui/artifacts/{groupId}/{artifactId}"
        groupId = URLDecoder.decode(slices[slices.length - 2], StandardCharsets.UTF_8.name());
        artifactId = URLDecoder.decode(slices[slices.length - 1], StandardCharsets.UTF_8.name());

        description = webItem.findElement(By.className("artifact-description")).getText();
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public WebElement getViewArtifactLink() {
        return viewArtifactLink;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ArtifactListItem [groupId=" + groupId + ", artifactId=" + artifactId + ", name=" + name
                + ", description=" + description + ", link=" + link + "]";
    }

    public boolean matches(String groupId, String artifactId) {
        if (groupId == null) {
            groupId = "default";
        }
        return this.groupId.equals(groupId) && this.artifactId.equals(artifactId);
    }

}
