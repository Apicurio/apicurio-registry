package io.apicurio.registry.storage.impl.gitops;

import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class GitTestRepository implements AutoCloseable {

    private Git git;

    @Getter
    private String gitRepoUrl;

    @Getter
    private String gitRepoBranch;

    public void initialize() {
        try {
            var gitDir = Files.createTempDirectory(null);
            gitRepoBranch = "main";
            git = Git.init().setDirectory(gitDir.toFile()).setInitialBranch(gitRepoBranch).call();
            Files.write(gitDir.resolve(".init"), "init".getBytes(StandardCharsets.UTF_8));
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Initial commit").call();
            gitRepoUrl = "file://" + git.getRepository().getWorkTree().getAbsolutePath();

        } catch (IOException | GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    public void load(String sourceDir) {
        try {
            var sourcePath = Path
                    .of(requireNonNull(Thread.currentThread().getContextClassLoader().getResource(sourceDir))
                            .toURI());
            var files = FileUtils.listFiles(git.getRepository().getWorkTree(), null, true);
            for (File f : files) {
                var prefix = Path.of(git.getRepository().getWorkTree().getPath(), ".git");
                if (!f.toPath().startsWith(prefix)) {
                    FileUtils.delete(f);
                }
            }
            FileUtils.copyDirectory(sourcePath.toFile(), git.getRepository().getWorkTree());
            git.add().setUpdate(true).addFilepattern(".").call();
            git.add().addFilepattern(".").call();
            git.commit().setMessage("test").call();
        } catch (IOException | GitAPIException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void close() throws Exception {
        if (git != null) {
            git.close();
            FileUtils.forceDelete(git.getRepository().getWorkTree());
        }
    }
}
