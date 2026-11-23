package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContractRuleDto;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import io.apicurio.registry.storage.dto.RuleAction;
import io.apicurio.registry.storage.dto.RuleKind;
import io.apicurio.registry.storage.dto.RuleMode;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
public class SqlEventOrderTest extends AbstractResourceTestBase {

    @Inject
    SqlOutboxEventRecorder recorder;

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    public void artifactCreatedIsFiredBeforeItsFirstVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifact(groupId, "orders");

        assertEquals(List.of("GROUP_CREATED", "ARTIFACT_CREATED", "ARTIFACT_VERSION_CREATED"),
                recorder.typesForGroup(groupId));
    }

    @Test
    public void deletingAllArtifactsOfAGroupFiresOneEventPerArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifact(groupId, "a");
        createArtifact(groupId, "b");

        storage.deleteArtifacts(groupId);

        List<String> types = recorder.typesForGroup(groupId);
        assertEquals(List.of("ARTIFACT_DELETED", "ARTIFACT_DELETED"), types.subList(types.size() - 2, types.size()));
    }

    @Test
    public void contractRulesetIsRolledBackWhenItsEventCannotBeWritten() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifact(groupId, "orders");
        ContractRuleSetDto ruleset = ContractRuleSetDto.builder().domainRules(List.of(ContractRuleDto.builder()
                .name("non-empty").kind(RuleKind.CONDITION).type("CEL").mode(RuleMode.WRITE)
                .expr("true").onSuccess(RuleAction.NONE).onFailure(RuleAction.ERROR).build()))
                .migrationRules(List.of()).build();

        recorder.failOn("CONTRACT_RULESET_CONFIGURED");
        try {
            assertThrows(IllegalStateException.class,
                    () -> storage.setArtifactContractRuleset(groupId, "orders", ruleset));
        } finally {
            recorder.failOn(null);
        }

        ContractRuleSetDto stored = storage.getArtifactContractRuleset(groupId, "orders");
        assertEquals(0, stored.getDomainRules().size());
    }

    private void createArtifact(String groupId, String artifactId) throws Exception {
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                "{\"type\": \"object\"}", ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }
}
