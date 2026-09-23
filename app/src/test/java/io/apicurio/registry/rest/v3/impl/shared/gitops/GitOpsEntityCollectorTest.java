package io.apicurio.registry.rest.v3.impl.shared.gitops;

import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.ContractRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitOpsEntityCollectorTest {

    @Test
    void collectGroupsEntitiesCorrectly() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final GroupEntity group = new GroupEntity();
        group.groupId = "test-group";
        group.description = "Test group";

        collector.collect(group);

        assertEquals(1, collector.getGroups().size());
        assertEquals("Test group", collector.getGroups().get("test-group").description);
    }

    @Test
    void collectArtifactsAndVersionsGroupedByKey() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final ArtifactEntity artifact = new ArtifactEntity();
        artifact.groupId = "g1";
        artifact.artifactId = "a1";
        artifact.artifactType = "JSON";

        final ArtifactVersionEntity v1 = new ArtifactVersionEntity();
        v1.groupId = "g1";
        v1.artifactId = "a1";
        v1.version = "1.0";
        v1.globalId = 1L;
        v1.contentId = 100L;

        final ArtifactVersionEntity v2 = new ArtifactVersionEntity();
        v2.groupId = "g1";
        v2.artifactId = "a1";
        v2.version = "2.0";
        v2.globalId = 2L;
        v2.contentId = 101L;

        collector.collect(artifact);
        collector.collect(v1);
        collector.collect(v2);

        assertEquals(1, collector.getArtifacts().size());
        final List<ArtifactVersionEntity> versions = collector.getVersions().get("g1:a1");
        assertNotNull(versions);
        assertEquals(2, versions.size());
    }

    @Test
    void collectContentByContentId() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final ContentEntity content = new ContentEntity();
        content.contentId = 42L;
        content.contentHash = "abc123";
        content.contentBytes = "test content".getBytes();

        collector.collect(content);

        assertEquals(1, collector.getContentById().size());
        assertNotNull(collector.getContentById().get(42L));
    }

    @Test
    void collectGlobalRules() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final GlobalRuleEntity rule = new GlobalRuleEntity();
        rule.ruleType = RuleType.VALIDITY;
        rule.configuration = "FULL";

        collector.collect(rule);

        assertEquals(1, collector.getGlobalRules().size());
    }

    @Test
    void collectGroupRulesGroupedByGroupId() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final GroupRuleEntity rule = new GroupRuleEntity();
        rule.groupId = "g1";
        rule.type = RuleType.COMPATIBILITY;
        rule.configuration = "BACKWARD";

        collector.collect(rule);

        final List<GroupRuleEntity> rules = collector.getGroupRules().get("g1");
        assertNotNull(rules);
        assertEquals(1, rules.size());
    }

    @Test
    void collectArtifactRulesGroupedByArtifactKey() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final ArtifactRuleEntity rule = new ArtifactRuleEntity();
        rule.groupId = "g1";
        rule.artifactId = "a1";
        rule.type = RuleType.VALIDITY;
        rule.configuration = "SYNTAX_ONLY";

        collector.collect(rule);

        final List<ArtifactRuleEntity> rules = collector.getArtifactRules().get("g1:a1");
        assertNotNull(rules);
        assertEquals(1, rules.size());
    }

    @Test
    void collectCommentsGroupedByGlobalId() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final CommentEntity comment = new CommentEntity();
        comment.globalId = 10L;
        comment.commentId = "c1";
        comment.value = "Looks good";

        collector.collect(comment);

        final List<CommentEntity> comments = collector.getComments().get(10L);
        assertNotNull(comments);
        assertEquals(1, comments.size());
        assertEquals("Looks good", comments.get(0).value);
    }

    @Test
    void collectBranchesGroupedByArtifactKey() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final BranchEntity branch = new BranchEntity();
        branch.groupId = "g1";
        branch.artifactId = "a1";
        branch.branchId = "release-1";

        collector.collect(branch);

        final List<BranchEntity> branches = collector.getBranches().get("g1:a1");
        assertNotNull(branches);
        assertEquals(1, branches.size());
    }

    @Test
    void contractRulesSkippedWithWarning() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        collector.collect(new ContractRuleEntity());
        collector.collect(new ContractRuleEntity());

        final List<String> warnings = collector.getWarnings();
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("2 contract rule(s)"));
    }

    @Test
    void manifestEntityIgnored() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final ManifestEntity manifest = new ManifestEntity();
        collector.collect(manifest);

        assertTrue(collector.getGroups().isEmpty());
        assertTrue(collector.getArtifacts().isEmpty());
        assertTrue(collector.getGlobalRules().isEmpty());
    }

    @Test
    void nullGroupIdMappedToDefault() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final GroupEntity group = new GroupEntity();
        group.groupId = null;

        final ArtifactEntity artifact = new ArtifactEntity();
        artifact.groupId = null;
        artifact.artifactId = "a1";

        collector.collect(group);
        collector.collect(artifact);

        assertTrue(collector.getGroups().containsKey("default"));
        assertTrue(collector.getArtifacts().containsKey("default:a1"));
    }

    @Test
    void noWarningsWhenNoContractRules() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final GroupEntity group = new GroupEntity();
        group.groupId = "g1";
        collector.collect(group);

        assertTrue(collector.getWarnings().isEmpty());
    }
}
