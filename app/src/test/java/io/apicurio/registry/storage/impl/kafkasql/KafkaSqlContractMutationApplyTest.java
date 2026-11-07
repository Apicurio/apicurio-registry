package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.dto.ContractRuleDto;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.dto.RuleAction;
import io.apicurio.registry.storage.dto.RuleKind;
import io.apicurio.registry.storage.dto.RuleMode;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every contract mutation still applies correctly when it reaches the SQL store through the
 * KafkaSQL journal.
 * <p>
 * The SQL storage now runs each contract mutation and its outbox write inside one outer transaction. The
 * journal applies those same methods on every node, so this test covers the replay direction: after the
 * coordinator confirms the message was applied, the committed state must be readable. The journaling
 * itself is covered by {@link KafkaSqlContractMetadataJournalTest} and
 * {@link KafkaSqlGlobalContractRulesetJournalTest}.
 * </p>
 */
@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class KafkaSqlContractMutationApplyTest extends AbstractResourceTestBase {

    private static final String GROUP = "default";
    private static final String STABLE_DATE = "2026-01-01";

    @Inject
    KafkaSqlRegistryStorage storage;

    @Test
    void testContractMetadataAndStatusAreApplied() throws Exception {
        String artifactId = "contract-apply-metadata-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.JSON, "{\"type\":\"object\"}",
                ContentTypes.APPLICATION_JSON);

        String prefix = ContractLabels.contractPrefix("apply");
        String ownerTeamKey = prefix + ContractLabels.SUFFIX_OWNER_TEAM;
        String statusKey = prefix + ContractLabels.SUFFIX_STATUS;
        String stableDateKey = prefix + ContractLabels.SUFFIX_STABLE_DATE;

        storage.updateContractMetadata(null, artifactId, prefix, Map.of(ownerTeamKey, "apply-team"));
        assertEquals("apply-team", labels(artifactId).get(ownerTeamKey));

        storage.transitionContractStatus(null, artifactId, null, ContractStatus.DRAFT.name(), prefix,
                STABLE_DATE);
        assertEquals(ContractStatus.DRAFT.name(), labels(artifactId).get(statusKey));

        storage.transitionContractStatus(null, artifactId, ContractStatus.DRAFT.name(),
                ContractStatus.STABLE.name(), prefix, STABLE_DATE);

        Map<String, String> labels = labels(artifactId);
        assertEquals(ContractStatus.STABLE.name(), labels.get(statusKey));
        assertEquals(STABLE_DATE, labels.get(stableDateKey));
        // The outer transaction must not have clobbered the label written by the earlier mutation.
        assertEquals("apply-team", labels.get(ownerTeamKey));
    }

    @Test
    void testArtifactAndVersionContractRulesetsAreApplied() throws Exception {
        String artifactId = "contract-apply-ruleset-" + UUID.randomUUID();
        var response = createArtifact(GROUP, artifactId, ArtifactType.JSON, "{\"type\":\"object\"}",
                ContentTypes.APPLICATION_JSON);
        String version = response.getVersion().getVersion();

        storage.setArtifactContractRuleset(null, artifactId, ruleset("artifact-rule"));
        assertSingleDomainRule("artifact-rule", storage.getArtifactContractRuleset(null, artifactId));

        storage.deleteArtifactContractRuleset(null, artifactId);
        assertTrue(storage.getArtifactContractRuleset(null, artifactId).getDomainRules().isEmpty());

        storage.setVersionContractRuleset(null, artifactId, version, ruleset("version-rule"));
        assertSingleDomainRule("version-rule",
                storage.getVersionContractRuleset(null, artifactId, version));

        storage.deleteVersionContractRuleset(null, artifactId, version);
        assertTrue(storage.getVersionContractRuleset(null, artifactId, version).getDomainRules()
                .isEmpty());
    }

    @Test
    void testGlobalContractRulesetIsApplied() {
        // The global ruleset is registry-wide state shared by every test in this profile, so the
        // delete has to run even when the read-back assertion fails.
        try {
            storage.setGlobalContractRuleset(ruleset("global-rule"));
            assertSingleDomainRule("global-rule", storage.getGlobalContractRuleset());
        } finally {
            storage.deleteGlobalContractRuleset();
        }
        assertTrue(storage.getGlobalContractRuleset().getDomainRules().isEmpty());
    }

    private Map<String, String> labels(String artifactId) {
        return storage.getArtifactMetaData(null, artifactId).getLabels();
    }

    private static ContractRuleSetDto ruleset(String ruleName) {
        return ContractRuleSetDto.builder()
                .domainRules(List.of(ContractRuleDto.builder().name(ruleName).kind(RuleKind.CONDITION)
                        .type("expression").mode(RuleMode.WRITE).expr("value != null")
                        .tags(Set.of("contract")).onFailure(RuleAction.ERROR).disabled(false).build()))
                .migrationRules(List.of()).build();
    }

    private static void assertSingleDomainRule(String expectedName, ContractRuleSetDto ruleSet) {
        assertEquals(1, ruleSet.getDomainRules().size(),
                "Expected exactly one domain rule, found " + ruleSet.getDomainRules());
        assertEquals(expectedName, ruleSet.getDomainRules().get(0).getName());
    }
}
