/*
 * Copyright 2018 Confluent Inc. (adapted from their Mojo)
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.maven;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Test artifact against current artifact rules,
 * if an update is possible / valid.
 *
 * @author Ales Justin
 */
@Mojo(name = "test-update")
public class TestUpdateRegistryMojo extends AbstractRegistryMojo {

    /**
     * The list of artifacts to test.
     */
    @Parameter(required = true)
    List<TestArtifact> artifacts;

    /**
     * Validate the configuration.
     */
    protected void validate() throws MojoExecutionException {
        if (artifacts == null || artifacts.isEmpty()) {
            getLog().warn("No artifacts are configured for testing/validation.");
        } else {
            int idx = 0;
            int errorCount = 0;
            for (TestArtifact artifact : artifacts) {
                if (artifact.getGroupId() == null) {
                    getLog().error(String.format("GroupId is required when testing an artifact.  Missing from artifacts[%d].", idx));
                    errorCount++;
                }
                if (artifact.getArtifactId() == null) {
                    getLog().error(String.format("ArtifactId is required when testing an artifact.  Missing from artifacts[%s].", idx));
                    errorCount++;
                }
                if (artifact.getFile() == null) {
                    getLog().error(String.format("File is required when testing an artifact.  Missing from artifacts[%s].", idx));
                    errorCount++;
                } else if (!artifact.getFile().isFile()) {
                    getLog().error(String.format("Artifact file to test is configured but file does not exist or is not a file: %s", artifact.getFile().getPath()));
                    errorCount++;
                }

                idx++;
            }

            if (errorCount > 0) {
                throw new MojoExecutionException("Invalid configuration of the Test Update Artifact(s) mojo. See the output log for details.");
            }
        }
    }

    @Override
    protected void executeInternal() throws MojoExecutionException {
        validate();

        int errorCount = 0;
        if (artifacts != null) {
            for (TestArtifact artifact : artifacts) {
                String groupId = artifact.getGroupId();
                String artifactId = artifact.getArtifactId();
                String contentType = contentType(artifact);
                try (InputStream data = new FileInputStream(artifact.getFile())) {
                    getClient().testUpdateArtifact(groupId, artifactId, contentType, data);
                    getLog().info(String.format("[%s] / [%s] :: Artifact successfully tested (updating is allowed for the given content).", groupId, artifactId));
                } catch (Exception e) {
                    errorCount++;
                    getLog().error(String.format("[%s] / [%s] :: Artifact test FAILED (updating is not allowed for the given content).", groupId, artifactId), e);
                }
            }
        }

        if (errorCount > 0) {
            throw new MojoExecutionException("Errors while testing artifacts for update...");
        }
    }

    private String contentType(TestArtifact testArtifact) {
        String contentType = testArtifact.getContentType();
        if(contentType != null) {
            return contentType;
        }
        return getContentTypeByExtension(testArtifact.getFile().getName());
    }

    public void setArtifacts(List<TestArtifact> artifacts) {
        this.artifacts = artifacts;
    }
}
