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
                    getClient().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).test().put(data, contentType);
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
