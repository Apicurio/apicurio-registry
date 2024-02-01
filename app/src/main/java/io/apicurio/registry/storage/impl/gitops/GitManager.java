package io.apicurio.registry.storage.impl.gitops;


import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.storage.impl.gitops.model.GitFile;
import io.apicurio.registry.storage.impl.gitops.model.Type;
import io.apicurio.registry.storage.impl.gitops.model.v0.Artifact;
import io.apicurio.registry.storage.impl.gitops.model.v0.Content;
import io.apicurio.registry.storage.impl.gitops.model.v0.Group;
import io.apicurio.registry.storage.impl.gitops.model.v0.Registry;
import io.apicurio.registry.storage.impl.gitops.model.v0.Rule;
import io.apicurio.registry.storage.impl.gitops.model.v0.Setting;
import io.apicurio.registry.storage.impl.gitops.model.v0.Version;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.ContentTypeUtil;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.apache.commons.io.FilenameUtils.concat;

@ApplicationScoped
public class GitManager {

    @Inject
    Logger log;

    @Inject
    GitOpsConfigProperties config;

    @Inject
    RegistryStorageContentUtils utils;

    private Git git;

    private String originRemoteName;

    @Getter
    private RevCommit previousCommit;


    public void start() throws IOException, URISyntaxException, GitAPIException {
        initRepo();
    }


    private void initRepo() throws IOException, GitAPIException, URISyntaxException {

        var workDirPath = Paths.get(config.getWorkDir());
        var gitPath = workDirPath.resolve("repo").resolve(".git");

        if (Files.exists(gitPath.resolve("config"))) {
            git = Git.open(gitPath.toFile());
        } else {
            git = Git.init()
                    .setGitDir(gitPath.toFile())
                    .setInitialBranch(UUID.randomUUID().toString())
                    .call();
        }

        var previousOID = git.getRepository().resolve("refs/heads/empty");
        if (previousOID == null) {

            git.commit()
                    .setMessage("empty")
                    .setAllowEmpty(true)
                    .call();

            git.checkout()
                    .setName("empty")
                    .setCreateBranch(true)
                    .setForced(true)
                    .setOrphan(true)
                    .call();

            previousOID = git.getRepository().resolve("refs/heads/empty");
        }

        previousCommit = git.getRepository().parseCommit(previousOID);
        originRemoteName = ensureRemote(config.getOriginRepoURI());
    }


    private String ensureRemote(String repoURI) throws GitAPIException, URISyntaxException {
        var repoURIish = new URIish(repoURI);
        var remote = git.remoteList()
                .call()
                .stream()
                .filter(r -> r.getURIs().stream().allMatch(u -> u.equals(repoURIish)))
                .findAny();
        if (remote.isPresent()) {
            return remote.get().getName();
        } else {
            var name = UUID.randomUUID().toString();
            git.remoteAdd()
                    .setName(name)
                    .setUri(repoURIish)
                    .call();
            return name;
        }
    }


    /**
     * Checks the configured origin repo branch and returns the corresponding latest RevCommit
     */
    public RevCommit poll() throws GitAPIException, IOException {

        var updatedRef = "refs/remotes/" + originRemoteName + "/" + config.getOriginRepoBranch();

        var fetchRef = "refs/heads/" + config.getOriginRepoBranch() + ":" + updatedRef;

        git.fetch()
                .setRemote(originRemoteName)
                .setRefSpecs(fetchRef)
                .setDepth(1)
                .setForceUpdate(true)
                .call();

        var updatedOID = git.getRepository().resolve(updatedRef);
        if (updatedOID == null) {
            throw new RuntimeException(String.format("Could not resolve %s", updatedRef));
        }
        return git.getRepository().parseCommit(updatedOID);
    }


    public void updateCurrentCommit(RevCommit currentCommit) {
        previousCommit = currentCommit;
    }


    public void run(ProcessingState state, RevCommit updatedCommit) throws GitAPIException, IOException {

        if (updatedCommit == null || updatedCommit.equals(previousCommit)) {
            throw new IllegalStateException("Make sure to call method pollUpdates() before calling me.");
        }

        log.debug("Processing change: {} -> {}", updatedCommit.name(), previousCommit.name());

        state.setUpdatedCommit(updatedCommit);

        try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
            treeWalk.addTree(updatedCommit.getTree());
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {

                var objectId = treeWalk.getObjectId(0);

                try (InputStream data = git.getRepository().getObjectDatabase().open(objectId).openStream()) {

                    var file = GitFile.create(state, treeWalk.getPathString(), data);
                    state.index(file);
                }
            }
        }

        log.debug("Processing {} files", state.getPathIndex().size());
        process(state);

        var unprocessed = state.getPathIndex().values().stream()
                .filter(f -> !f.isProcessed())
                .map(GitFile::getPath)
                .collect(Collectors.toList());

        log.debug("The following {} file(s) were not processed: {}", unprocessed.size(), unprocessed);
    }


    private void process(ProcessingState state) {

        for (GitFile file : state.fromTypeIndex(Type.REGISTRY)) {
            Registry registry = file.getEntityUnchecked();
            if (config.getRegistryId().equals(registry.getId())) {
                state.setCurrentRegistry(registry);
                file.setProcessed(true);
            }
        }

        if (state.getCurrentRegistry() != null) {

            processSettings(state);
            processGlobalRules(state);

            for (GitFile file : state.fromTypeIndex(Type.ARTIFACT)) {
                Artifact artifact = file.getEntityUnchecked();

                if (state.isCurrentRegistryId(artifact.getRegistryId())) {
                    processArtifact(state, file, artifact);
                } else {
                    log.debug("Ignoring {}", artifact);
                }
            }

        } else {
            log.warn("Git repository does not contain data for this registry (ID = {})", config.getRegistryId());
        }
    }


    private void processSettings(ProcessingState state) {
        var settings = state.getCurrentRegistry().getSettings();
        if (settings != null) {
            for (Setting setting : settings) {
                try {
                    var dto = new DynamicConfigPropertyDto();
                    dto.setName(setting.getName());
                    dto.setValue(setting.getValue());
                    log.debug("Importing {}", dto);
                    state.getStorage().setConfigProperty(dto);
                } catch (Exception ex) {
                    state.recordError("Could not import configuration property %s: %s", setting.getName(), ex.getMessage());
                }
            }
        }
    }


    private void processGlobalRules(ProcessingState state) {
        var globalRules = state.getCurrentRegistry().getGlobalRules();
        if (globalRules != null) {
            for (Rule globalRule : globalRules) {
                try {
                    var e = new GlobalRuleEntity();
                    e.ruleType = RuleType.fromValue(globalRule.getType());
                    e.configuration = globalRule.getConfig();
                    log.debug("Importing {}", e);
                    state.getStorage().importGlobalRule(e);
                } catch (Exception ex) {
                    state.recordError("Could not import global rule %s: %s", globalRule.getType(), ex.getMessage());
                }
            }
        }
    }


    private void processArtifact(ProcessingState state, GitFile artifactFile, Artifact artifact) {
        var group = processGroupRef(state, artifact.getGroupId());
        if (group != null) {

            List<Version> versions = artifact.getVersions();
            for (int i = 0; i < versions.size(); i++) {
                Version version = versions.get(i);
                try {
                    var e = new ArtifactVersionEntity();
                    e.groupId = artifact.getGroupId();
                    e.artifactId = artifact.getId();
                    e.version = version.getId();
                    e.globalId = version.getGlobalId();
                    e.state = ArtifactState.ENABLED;
                    e.createdOn = state.getUpdatedCommit().getCommitTime();

                    var content = processContent(state, artifactFile, version.getContentFile());
                    if (content != null) {

                        e.artifactType = content.getArtifactType();
                        e.contentId = content.getId();

                        log.debug("Importing {}", e);
                        state.getStorage().importArtifactVersion(e);

                    } else {
                        state.recordError("Could not import content for artifact version %s.",
                                artifact.getGroupId() + ":" + artifact.getId() + ":" + version.getId());
                        // Not necessary to clean up, we do not allow partial imports.
                    }
                } catch (Exception ex) {
                    state.recordError("Could not import artifact version '%s': %s",
                            artifact.getGroupId() + ":" + artifact.getId() + ":" + version.getId(), ex.getMessage());
                }
            }
            processArtifactRules(state, artifact);
            artifactFile.setProcessed(true);

        } else {
            state.recordError("Could not find group %s", artifact.getGroupId());
        }
    }


    private void processArtifactRules(ProcessingState state, Artifact artifact) {
        var rules = artifact.getRules();
        if (rules != null) {
            for (Rule rule : rules) {
                try {
                    var e = new ArtifactRuleEntity();
                    e.groupId = artifact.getGroupId();
                    e.artifactId = artifact.getId();
                    e.type = RuleType.fromValue(rule.getType());
                    e.configuration = rule.getConfig();
                    log.debug("Importing {}", e);
                    state.getStorage().importArtifactRule(e);
                } catch (Exception ex) {
                    state.recordError("Could not import rule %s for artifact '%s': %s",
                            rule.getType(), artifact.getGroupId() + ":" + artifact.getId(), ex.getMessage());
                }
            }
        }
    }


    private Group processGroupRef(ProcessingState state, String groupName) {
        var groupFiles = state.fromTypeIndex(Type.GROUP).stream()
                .filter(f -> {
                    Group group = f.getEntityUnchecked();
                    return state.isCurrentRegistryId(group.getRegistryId()) && groupName.equals(group.getId());
                })
                .collect(Collectors.toList());

        if (groupFiles.isEmpty()) {
            state.recordError("Could not find group with ID %s in registry %s", groupName, state.getCurrentRegistry().getId());
            return null;
        } else if (groupFiles.size() > 1) {
            state.recordError("Multiple groups with ID %s found in registry %s: %s", groupName, state.getCurrentRegistry().getId(), groupFiles);
            return null;
        } else {
            var groupFile = groupFiles.get(0);
            Group group = groupFile.getEntityUnchecked();
            if (groupFile.isProcessed()) {
                return group;
            }
            try {
                var e = new GroupEntity();
                e.groupId = group.getId();
                log.debug("Importing {}", e);
                state.getStorage().importGroup(e);
                groupFile.setProcessed(true);
                return group;
            } catch (Exception ex) {
                state.recordError("Could not import group %s: %s", group.getId(), ex.getMessage());
                return null;
            }
        }
    }


    private Content processContent(ProcessingState state, GitFile base, String contentRef) {
        var contentFile = findFileByPathRef(state, base, contentRef);
        if (contentFile != null) {
            if (contentFile.isType(Type.CONTENT)) {
                Content content = contentFile.getEntityUnchecked();
                if (state.isCurrentRegistryId(content.getRegistryId())) {
                    if (!contentFile.isProcessed()) {
                        var dataFile = findFileByPathRef(state, contentFile, content.getDataFile());
                        if (dataFile != null) {
                            var data = dataFile.getData();
                            if (ContentTypeUtil.isParsableYaml(data)) {
                                data = ContentTypeUtil.yamlToJson(data);
                            }
                            try {
                                var e = new ContentEntity();
                                e.contentId = content.getId();
                                e.contentHash = content.getContentHash();
                                e.contentBytes = data.bytes();
                                content.setArtifactType(utils.determineArtifactType(data, content.getArtifactType()));
                                e.canonicalHash = utils.getCanonicalContentHash(data, content.getArtifactType(), null, null);
                                e.artifactType = content.getArtifactType();
                                log.debug("Importing {}", e);
                                state.getStorage().importContent(e);
                                contentFile.setProcessed(true);
                                dataFile.setProcessed(true);
                                return content;
                            } catch (Exception ex) {
                                state.recordError("Could not import content %s: %s", contentFile.getPath(), ex.getMessage());
                                return null;
                            }
                        } else {
                            state.recordError("Could not find content data file at path %s referenced by %s",
                                    concat(concat(contentFile.getPath(), ".."), content.getDataFile()),
                                    contentFile.getPath());
                            return null;
                        }
                    } else {
                        return content;
                    }
                } else {
                    state.recordError("Content file %s does not belong to this registry", contentFile.getPath());
                    return null;
                }
            } else {
                state.recordError("File %s is not a valid content definition", contentFile.getPath());
                return null;
            }
        } else {
            state.recordError("Could not find content file at path %s",
                    concat(concat(base.getPath(), ".."), contentRef));
            return null;
        }
    }


    private GitFile findFileByPathRef(ProcessingState state, GitFile base, String path) {
        path = concat(concat(base.getPath(), ".."), path);
        return state.getPathIndex().get(path);
    }


    @PreDestroy
    public void close() {
        if (git != null) {
            git.close();
        }
    }
}
