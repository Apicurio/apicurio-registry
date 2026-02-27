package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.storage.impl.polling.AbstractDataSourceManager;
import io.apicurio.registry.storage.impl.polling.DataFile;
import io.apicurio.registry.storage.impl.polling.PollResult;
import io.apicurio.registry.storage.impl.gitops.model.GitFile;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GitManager extends AbstractDataSourceManager {

    @Inject
    Logger log;

    @Inject
    GitOpsConfigProperties config;

    private Git git;

    private String originRemoteName;

    private RevCommit previousCommit;

    @Override
    protected String getRegistryId() {
        return config.getRegistryId();
    }

    @Override
    protected long getCommitTime(Object marker) {
        return ((RevCommit) marker).getCommitTime();
    }

    public void start() throws IOException, URISyntaxException, GitAPIException {
        initRepo();
    }

    private void initRepo() throws IOException, GitAPIException, URISyntaxException {

        var workDirPath = Paths.get(config.getWorkDir());
        var gitPath = workDirPath.resolve("repo").resolve(".git");

        if (Files.exists(gitPath.resolve("config"))) {
            git = Git.open(gitPath.toFile());
        } else {
            git = Git.init().setGitDir(gitPath.toFile()).setInitialBranch(UUID.randomUUID().toString())
                    .call();
        }

        var previousOID = git.getRepository().resolve("refs/heads/empty");
        if (previousOID == null) {

            git.commit().setMessage("empty").setAllowEmpty(true).call();

            git.checkout().setName("empty").setCreateBranch(true).setForced(true).setOrphan(true).call();

            previousOID = git.getRepository().resolve("refs/heads/empty");
        }

        previousCommit = git.getRepository().parseCommit(previousOID);
        originRemoteName = ensureRemote(config.getOriginRepoURI());
    }

    private String ensureRemote(String repoURI) throws GitAPIException, URISyntaxException {
        var repoURIish = new URIish(repoURI);
        var remote = git.remoteList().call().stream()
                .filter(r -> r.getURIs().stream().allMatch(u -> u.equals(repoURIish))).findAny();
        if (remote.isPresent()) {
            return remote.get().getName();
        } else {
            var name = UUID.randomUUID().toString();
            git.remoteAdd().setName(name).setUri(repoURIish).call();
            return name;
        }
    }

    @Override
    public PollResult poll() throws Exception {
        var updatedRef = "refs/remotes/" + originRemoteName + "/" + config.getOriginRepoBranch();
        var fetchRef = "refs/heads/" + config.getOriginRepoBranch() + ":" + updatedRef;

        git.fetch().setRemote(originRemoteName).setRefSpecs(fetchRef).setDepth(1).setForceUpdate(true).call();

        var updatedOID = git.getRepository().resolve(updatedRef);
        if (updatedOID == null) {
            throw new RuntimeException(String.format("Could not resolve %s", updatedRef));
        }
        RevCommit updatedCommit = git.getRepository().parseCommit(updatedOID);

        if (updatedCommit.equals(previousCommit)) {
            return PollResult.noChanges(updatedCommit);
        }

        log.debug("Detected change: {} -> {}", updatedCommit.name(),
                previousCommit != null ? previousCommit.name() : "null");

        List<DataFile> files = new ArrayList<>();
        ProcessingState tempState = new ProcessingState(null);

        try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
            treeWalk.addTree(updatedCommit.getTree());
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                var objectId = treeWalk.getObjectId(0);
                try (InputStream data = git.getRepository().getObjectDatabase().open(objectId).openStream()) {
                    var file = GitFile.create(tempState, treeWalk.getPathString(), data);
                    files.add(file);
                }
            }
        }

        return PollResult.withChanges(updatedCommit, files);
    }

    @Override
    public void commitChange(Object marker) {
        if (marker instanceof RevCommit) {
            previousCommit = (RevCommit) marker;
        }
    }

    @Override
    public Object getPreviousMarker() {
        return previousCommit;
    }

    @PreDestroy
    public void close() {
        if (git != null) {
            git.close();
        }
    }
}
