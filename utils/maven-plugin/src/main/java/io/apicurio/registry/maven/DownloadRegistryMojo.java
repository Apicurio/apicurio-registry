package io.apicurio.registry.maven;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Download artifacts.
 *
 */
@Mojo(name = "download")
public class DownloadRegistryMojo extends AbstractRegistryMojo {

    /**
     * Set of artifact ids to download.
     */
    @Parameter(required = true)
    List<DownloadArtifact> artifacts;

    /**
     * Validate the configuration.
     */
    protected void validate() throws MojoExecutionException {
        if (artifacts == null || artifacts.isEmpty()) {
            getLog().warn("No artifacts are configured for download.");
        } else {
            int idx = 0;
            int errorCount = 0;
            for (DownloadArtifact artifact : artifacts) {
                if (artifact.getGroupId() == null) {
                    getLog().error(String.format("GroupId is required when downloading an artifact.  Missing from artifacts[%d].", idx));
                    errorCount++;
                }
                if (artifact.getArtifactId() == null) {
                    getLog().error(String.format("ArtifactId is required when downloading an artifact.  Missing from artifacts[%s].", idx));
                    errorCount++;
                }
                if (artifact.getFile() == null) {
                    getLog().error(String.format("File is required when downloading an artifact.  Missing from artifacts[%s].", idx));
                    errorCount++;
                } else {
                    if (artifact.getFile().exists()) {
                        if (artifact.getOverwrite() == null || artifact.getOverwrite() == false) {
                            getLog().error(String.format("File being written already exists.  Use <overwrite>true</overwrite> to replace the destination file: %s", artifact.getFile().getPath()));
                            errorCount++;
                        }
                    }
                }

                idx++;
            }

            if (errorCount > 0) {
                throw new MojoExecutionException("Invalid configuration of the Download Artifact(s) mojo. See the output log for details.");
            }
        }
    }

    @Override
    protected void executeInternal() throws MojoExecutionException, ExecutionException, InterruptedException {
        validate();

        int errorCount = 0;
        if (artifacts != null) {
            for (DownloadArtifact artifact : artifacts) {
                errorCount += downloadArtifact(artifact);
            }
        }

        if (errorCount > 0) {
            throw new MojoExecutionException("Errors while downloading artifacts ...");
        }
    }

    private int downloadArtifact(DownloadArtifact artifact) throws ExecutionException, InterruptedException {
        int errorCount = 0;
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();
        boolean replaceExisting = artifact.getOverwrite() != null && artifact.getOverwrite();

        getLog().info(String.format("Downloading artifact [%s] / [%s] (version %s).", groupId, artifactId, version));

        try (InputStream content = version == null ?
                getClient().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get() :
                getClient().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(version).get()) {

            if (!artifact.getFile().getParentFile().exists()) {
                artifact.getFile().getParentFile().mkdirs();
            }

            if (replaceExisting) {
                Files.copy(content, artifact.getFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(content, artifact.getFile().toPath());
            }
        } catch (Exception e) {
            errorCount++;
            getLog().error(String.format("Exception while downloading artifact [%s] / [%s]", groupId, artifactId), e);
        }

        getLog().info(String.format("Downloaded artifact [%s] / [%s] to %s.", groupId, artifactId, artifact.getFile()));

        if (artifact.getArtifactReferences() != null && !artifact.getArtifactReferences().isEmpty()) {
            for (DownloadArtifact reference: artifact.getArtifactReferences()) {
                errorCount += downloadArtifact(reference);
            }
        }

        return errorCount;
    }

    public void setArtifacts(List<DownloadArtifact> artifacts) {
        this.artifacts = artifacts;
    }
}
