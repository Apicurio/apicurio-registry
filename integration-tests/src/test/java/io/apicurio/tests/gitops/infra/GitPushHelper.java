package io.apicurio.tests.gitops.infra;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class GitPushHelper implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitPushHelper.class);

    private final String remoteUrl;
    private Git git;
    private Path workDir;

    public GitPushHelper(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    private void ensureCloned() throws GitAPIException, IOException, URISyntaxException {
        if (git != null) {
            return;
        }
        workDir = Files.createTempDirectory("gitops-push-");
        LOGGER.info("Cloning {} to {}", remoteUrl, workDir);
        git = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(workDir.toFile())
                .setBranch("main")
                .call();
        git.remoteSetUrl()
                .setRemoteName("origin")
                .setRemoteUri(new URIish(remoteUrl))
                .call();
    }

    public void addFile(String path, String content) throws Exception {
        ensureCloned();
        final Path file = workDir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        git.add().addFilepattern(path).call();
    }

    public void removeFile(String path) throws Exception {
        ensureCloned();
        git.rm().addFilepattern(path).call();
    }

    public void pushCommit(String message) throws Exception {
        ensureCloned();
        git.pull().setRemote("origin").call();
        git.commit().setMessage(message)
                .setAuthor("test", "test@apicurio.io")
                .call();
        final var result = git.push().setRemote("origin").call();
        LOGGER.info("Push result: {}", result);
    }

    @Override
    public void close() {
        if (git != null) {
            git.close();
        }
        if (workDir != null) {
            try {
                Files.walk(workDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                LOGGER.trace("Could not delete {}", p, e);
                            }
                        });
            } catch (IOException e) {
                LOGGER.warn("Error cleaning work dir", e);
            }
        }
    }
}
