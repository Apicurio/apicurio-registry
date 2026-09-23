package io.apicurio.registry.rest.v3.impl.shared.gitops;

import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GitOpsEntityCollector {

    private final Map<Long, ContentEntity> contentById = new LinkedHashMap<>();
    private final Map<String, GroupEntity> groups = new LinkedHashMap<>();
    private final Map<String, List<GroupRuleEntity>> groupRules = new LinkedHashMap<>();
    private final Map<String, ArtifactEntity> artifacts = new LinkedHashMap<>();
    private final Map<String, List<ArtifactVersionEntity>> versions = new LinkedHashMap<>();
    private final Map<String, List<ArtifactRuleEntity>> artifactRules = new LinkedHashMap<>();
    private final Map<Long, List<CommentEntity>> comments = new LinkedHashMap<>();
    private final Map<String, List<BranchEntity>> branches = new LinkedHashMap<>();
    private final List<GlobalRuleEntity> globalRules = new ArrayList<>();
    private int skippedContractRuleCount = 0;

    public void collect(Entity entity) {
        switch (entity.getEntityType()) {
            case Content -> {
                final ContentEntity ce = (ContentEntity) entity;
                contentById.put(ce.contentId, ce);
            }
            case Group -> {
                final GroupEntity ge = (GroupEntity) entity;
                groups.put(groupKey(ge.groupId), ge);
            }
            case GroupRule -> {
                final GroupRuleEntity gre = (GroupRuleEntity) entity;
                groupRules.computeIfAbsent(groupKey(gre.groupId), k -> new ArrayList<>()).add(gre);
            }
            case Artifact -> {
                final ArtifactEntity ae = (ArtifactEntity) entity;
                artifacts.put(artifactKey(ae.groupId, ae.artifactId), ae);
            }
            case ArtifactVersion -> {
                final ArtifactVersionEntity ave = (ArtifactVersionEntity) entity;
                versions.computeIfAbsent(artifactKey(ave.groupId, ave.artifactId), k -> new ArrayList<>())
                        .add(ave);
            }
            case ArtifactRule -> {
                final ArtifactRuleEntity are = (ArtifactRuleEntity) entity;
                artifactRules
                        .computeIfAbsent(artifactKey(are.groupId, are.artifactId), k -> new ArrayList<>())
                        .add(are);
            }
            case Comment -> {
                final CommentEntity ce = (CommentEntity) entity;
                comments.computeIfAbsent(ce.globalId, k -> new ArrayList<>()).add(ce);
            }
            case Branch -> {
                final BranchEntity be = (BranchEntity) entity;
                branches.computeIfAbsent(artifactKey(be.groupId, be.artifactId), k -> new ArrayList<>())
                        .add(be);
            }
            case GlobalRule -> globalRules.add((GlobalRuleEntity) entity);
            case ContractRule -> skippedContractRuleCount++;
            case Manifest -> {
                // Ignored — not needed in GitOps format
            }
        }
    }

    public List<String> getWarnings() {
        final List<String> warnings = new ArrayList<>();
        if (skippedContractRuleCount > 0) {
            warnings.add("Skipped " + skippedContractRuleCount
                    + " contract rule(s) — not supported in GitOps format.");
        }
        return warnings;
    }

    public Map<Long, ContentEntity> getContentById() {
        return contentById;
    }

    public Map<String, GroupEntity> getGroups() {
        return groups;
    }

    public Map<String, List<GroupRuleEntity>> getGroupRules() {
        return groupRules;
    }

    public Map<String, ArtifactEntity> getArtifacts() {
        return artifacts;
    }

    public Map<String, List<ArtifactVersionEntity>> getVersions() {
        return versions;
    }

    public Map<String, List<ArtifactRuleEntity>> getArtifactRules() {
        return artifactRules;
    }

    public Map<Long, List<CommentEntity>> getComments() {
        return comments;
    }

    public Map<String, List<BranchEntity>> getBranches() {
        return branches;
    }

    public List<GlobalRuleEntity> getGlobalRules() {
        return globalRules;
    }

    static String groupKey(String groupId) {
        return groupId == null ? "default" : groupId;
    }

    static String artifactKey(String groupId, String artifactId) {
        return groupKey(groupId) + ":" + artifactId;
    }
}
