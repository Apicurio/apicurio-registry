package io.apicurio.tests.smokeTests.apicurio;

import io.apicurio.registry.rest.client.models.ContractMetadata;
import io.apicurio.registry.rest.client.models.ContractRule;
import io.apicurio.registry.rest.client.models.ContractRuleKind;
import io.apicurio.registry.rest.client.models.ContractRuleMode;
import io.apicurio.registry.rest.client.models.ContractRuleOnFailure;
import io.apicurio.registry.rest.client.models.ContractRuleSet;
import io.apicurio.registry.rest.client.models.OdcsContractResult;
import io.apicurio.registry.rest.client.models.OdcsContractSummary;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.contract.promote.PromotePostRequestBody;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.contract.promote.PromotePostRequestBodyTargetStage;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.contract.promote.PromotePostResponse;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.apicurio.deployment.Constants.SMOKE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(SMOKE)
@QuarkusIntegrationTest
class OdcsContractIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(OdcsContractIT.class);

    private static final String AVRO_SCHEMA = """
            {
              "type": "record",
              "name": "OrderEvent",
              "fields": [
                {"name": "orderId", "type": "string"},
                {"name": "customerEmail", "type": "string", "tags": ["PII", "EMAIL"]},
                {"name": "totalAmount", "type": "double"}
              ]
            }
            """;

    private String createOdcsContract(String schemaGroupId, String schemaArtifactId, String contractId) {
        return "apiVersion: v3.1.0\n"
                + "kind: DataContract\n"
                + "id: " + contractId + "\n"
                + "info:\n"
                + "  title: Test Contract\n"
                + "  version: 1.0.0\n"
                + "  status: active\n"
                + "  dataClassification: confidential\n"
                + "team:\n"
                + "  name: test-team\n"
                + "  domain: testing\n"
                + "  contact: test@example.com\n"
                + "schemas:\n"
                + "  - name: OrderEvent\n"
                + "    type: avro\n"
                + "    location: " + schemaGroupId + "/" + schemaArtifactId + ":latest\n"
                + "    fields:\n"
                + "      customerEmail:\n"
                + "        pii: true\n"
                + "        tags:\n"
                + "          - PII\n"
                + "          - EMAIL\n"
                + "quality:\n"
                + "  accuracy:\n"
                + "    - name: positive-amount\n"
                + "      expression: totalAmount > 0\n"
                + "      threshold: 1.0\n"
                + "serviceLevel:\n"
                + "  availability: 0.999\n";
    }

    private void createSchemaArtifact(String groupId, String artifactId) throws Exception {
        createArtifact(groupId, artifactId, ArtifactType.AVRO, AVRO_SCHEMA,
                ContentTypes.APPLICATION_JSON, null, null);
    }

    private OdcsContractResult submitContract(String groupId, String contractYaml) {
        return registryClient.groups().byGroupId(groupId)
                .contracts()
                .post(new ByteArrayInputStream(contractYaml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testSubmitAndGetContract() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "submit-get-" + UUID.randomUUID();
        String contractId = "contract-" + UUID.randomUUID();

        createSchemaArtifact(groupId, artifactId);
        OdcsContractResult result = submitContract(groupId,
                createOdcsContract(groupId, artifactId, contractId));
        assertNotNull(result.getContractId());

        retry(() -> {
            InputStream contractStream = registryClient.groups().byGroupId(groupId)
                    .contracts().byContractId(contractId).get();
            assertNotNull(contractStream);
        });
    }

    @Test
    void testListContracts() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "list-contracts-" + UUID.randomUUID();
        String contractId = "contract-" + UUID.randomUUID();

        createSchemaArtifact(groupId, artifactId);
        submitContract(groupId, createOdcsContract(groupId, artifactId, contractId));

        retry(() -> {
            List<OdcsContractSummary> contracts = registryClient.groups()
                    .byGroupId(groupId).contracts().get();
            assertNotNull(contracts);
            assertFalse(contracts.isEmpty());
        });
    }

    @Test
    void testUpdateContract() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "update-contract-" + UUID.randomUUID();
        String contractId = "contract-" + UUID.randomUUID();

        createSchemaArtifact(groupId, artifactId);
        submitContract(groupId, createOdcsContract(groupId, artifactId, contractId));

        retry(() -> {
            String updatedContract = createOdcsContract(groupId, artifactId, contractId)
                    .replace("title: Test Contract", "title: Updated Contract")
                    .replace("version: 1.0.0", "version: 2.0.0");

            OdcsContractResult updated = registryClient.groups().byGroupId(groupId)
                    .contracts().byContractId(contractId)
                    .put(new ByteArrayInputStream(updatedContract.getBytes(StandardCharsets.UTF_8)));
            assertNotNull(updated.getContractId());
        });
    }

    @Test
    void testDeleteContract() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "delete-contract-" + UUID.randomUUID();
        String contractId = "contract-" + UUID.randomUUID();

        createSchemaArtifact(groupId, artifactId);
        submitContract(groupId, createOdcsContract(groupId, artifactId, contractId));

        retry(() -> registryClient.groups().byGroupId(groupId)
                .contracts().byContractId(contractId).delete());

        retry(() -> assertThrows(Exception.class, () ->
                registryClient.groups().byGroupId(groupId)
                        .contracts().byContractId(contractId).get()));
    }

    @Test
    void testExportContractAsOdcs() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "export-contract-" + UUID.randomUUID();
        String contractId = "contract-" + UUID.randomUUID();

        createSchemaArtifact(groupId, artifactId);
        submitContract(groupId, createOdcsContract(groupId, artifactId, contractId));

        retry(() -> {
            InputStream exported = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .contract().export().get();
            assertNotNull(exported);
            String yaml = new BufferedReader(new InputStreamReader(exported, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            assertTrue(yaml.contains("DataContract"), "Exported YAML should contain DataContract");
        });
    }

    @Test
    void testContractMetadata() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "metadata-contract-" + UUID.randomUUID();
        String contractId = "contract-" + UUID.randomUUID();

        createSchemaArtifact(groupId, artifactId);
        submitContract(groupId, createOdcsContract(groupId, artifactId, contractId));

        retry(() -> {
            ContractMetadata metadata = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .contract().metadata().get();
            assertNotNull(metadata);
            assertNotNull(metadata.getStatus());
        });
    }

    @Test
    void testContractRuleset() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "ruleset-contract-" + UUID.randomUUID();

        createSchemaArtifact(groupId, artifactId);

        ContractRuleSet ruleset = new ContractRuleSet();
        ContractRule rule = new ContractRule();
        rule.setName("positive-amount");
        rule.setKind(ContractRuleKind.CONDITION);
        rule.setType("CEL");
        rule.setMode(ContractRuleMode.WRITE);
        rule.setExpr("record.totalAmount > 0");
        rule.setOnFailure(ContractRuleOnFailure.ERROR);
        ruleset.setDomainRules(List.of(rule));

        ContractRuleSet created = registryClient.groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .contract().ruleset().put(ruleset);
        assertNotNull(created);
        assertEquals(1, created.getDomainRules().size());

        retry(() -> {
            ContractRuleSet fetched = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .contract().ruleset().get();
            assertEquals(1, fetched.getDomainRules().size());
            assertEquals("positive-amount", fetched.getDomainRules().get(0).getName());
        });

        registryClient.groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .contract().ruleset().delete();
    }

    @Test
    void testContractQualityScore() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "quality-contract-" + UUID.randomUUID();
        String contractId = "contract-" + UUID.randomUUID();

        createSchemaArtifact(groupId, artifactId);
        submitContract(groupId, createOdcsContract(groupId, artifactId, contractId));

        retry(() -> {
            var quality = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .contract().quality().get(config -> {
                        config.queryParameters.contractId = contractId;
                    });
            assertNotNull(quality);
            assertNotNull(quality.getOverall());
        });
    }

    @Test
    void testPromoteContract() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "promote-contract-" + UUID.randomUUID();
        String contractId = "contract-" + UUID.randomUUID();

        createSchemaArtifact(groupId, artifactId);
        submitContract(groupId, createOdcsContract(groupId, artifactId, contractId));

        retry(() -> {
            PromotePostRequestBody devBody = new PromotePostRequestBody();
            devBody.setContractId(contractId);
            devBody.setTargetStage(PromotePostRequestBodyTargetStage.DEV);
            PromotePostResponse devResult = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .contract().promote().post(devBody);
            assertEquals("DEV", devResult.getStage());
        });

        PromotePostRequestBody stageBody = new PromotePostRequestBody();
        stageBody.setContractId(contractId);
        stageBody.setTargetStage(PromotePostRequestBodyTargetStage.STAGE);
        PromotePostResponse stageResult = registryClient.groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .contract().promote().post(stageBody);
        assertEquals("STAGE", stageResult.getStage());
    }

    @Test
    void testPromoteInvalidStageThrows() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "promote-invalid-" + UUID.randomUUID();

        createSchemaArtifact(groupId, artifactId);

        PromotePostRequestBody body = new PromotePostRequestBody();
        body.setContractId("test");
        body.setTargetStage(PromotePostRequestBodyTargetStage.forValue("INVALID"));

        assertThrows(Exception.class, () ->
                registryClient.groups().byGroupId(groupId)
                        .artifacts().byArtifactId(artifactId)
                        .contract().promote().post(body));
    }

    @Test
    void testSubmitInvalidYamlThrows() {
        String groupId = TestUtils.generateGroupId();

        assertThrows(Exception.class, () ->
                registryClient.groups().byGroupId(groupId)
                        .contracts()
                        .post(new ByteArrayInputStream("not valid yaml {{{".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void testSubmitContractWithMissingSchemaReturnsWarnings() {
        String groupId = TestUtils.generateGroupId();
        String contractId = "contract-" + UUID.randomUUID();
        String contract = createOdcsContract(groupId, "nonexistent-artifact", contractId);

        OdcsContractResult result = submitContract(groupId, contract);
        assertNotNull(result);
        assertNotNull(result.getProjection().getWarnings());
        assertFalse(result.getProjection().getWarnings().isEmpty());
    }

    @Test
    void testFullContractLifecycle() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "lifecycle-" + UUID.randomUUID();
        String contractId = "contract-" + UUID.randomUUID();

        LOGGER.info("Starting full contract lifecycle test: groupId={}, artifactId={}", groupId, artifactId);

        createSchemaArtifact(groupId, artifactId);

        OdcsContractResult result = submitContract(groupId,
                createOdcsContract(groupId, artifactId, contractId));
        assertNotNull(result.getContractId());

        retry(() -> {
            ContractMetadata metadata = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .contract().metadata().get();
            assertNotNull(metadata.getStatus());
        });

        retry(() -> {
            var quality = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .contract().quality().get(config -> {
                        config.queryParameters.contractId = contractId;
                    });
            assertNotNull(quality.getOverall());
        });

        retry(() -> {
            PromotePostRequestBody devBody = new PromotePostRequestBody();
            devBody.setContractId(contractId);
            devBody.setTargetStage(PromotePostRequestBodyTargetStage.DEV);
            PromotePostResponse devResult = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .contract().promote().post(devBody);
            assertEquals("DEV", devResult.getStage());
        });

        retry(() -> {
            InputStream exported = registryClient.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .contract().export().get();
            String yaml = new BufferedReader(new InputStreamReader(exported, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            assertTrue(yaml.contains("DataContract"), "Exported YAML should contain DataContract");
        });

        registryClient.groups().byGroupId(groupId)
                .contracts().byContractId(contractId).delete();

        LOGGER.info("Full contract lifecycle test completed successfully");
    }
}
