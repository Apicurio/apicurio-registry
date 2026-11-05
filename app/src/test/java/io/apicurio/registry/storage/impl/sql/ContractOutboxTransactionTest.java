package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.events.ContractRulesetConfigured;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.ContractRuleDto;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.dto.RuleAction;
import io.apicurio.registry.storage.dto.RuleKind;
import io.apicurio.registry.storage.dto.RuleMode;
import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that each contract mutation in {@link AbstractSqlRegistryStorage} shares a single SQL
 * transaction with the {@link SqlOutboxEvent} write it triggers, so that a failure on either side leaves
 * no partially applied change.
 * <p>
 * The failures are injected into the real database rather than simulated: a CHECK constraint on the
 * {@code outbox} table makes the production outbox INSERT fail where it really happens (inside
 * {@code SqlEventRepository.createEvent}, reached through the CDI observer
 * {@code SqlEventsProcessor}). That also proves the outbox write takes place at all — without it, the
 * operation under test would simply succeed and the rollback assertions would be vacuous. The
 * constraint is scoped to a single event type for the reason given on
 * {@link #blockOutboxWritesFor(StorageEventType)}.
 * </p>
 * <p>
 * Every rollback test here has been confirmed to fail against the unwrapped storage code, so none of
 * them can pass without the shared transaction they are asserting.
 * </p>
 * <p>
 * PostgreSQL is required because it is one of the two dialects for which the SQL outbox path is active;
 * on the others {@code supportsDatabaseEvents()} is false and no outbox row is ever written.
 * </p>
 */
@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(PostgresqlTestProfile.class)
public class ContractOutboxTransactionTest {

    /** PostgreSQL SQL state for a check-constraint violation. */
    private static final String CHECK_VIOLATION_SQL_STATE = "23514";

    private static final String OUTBOX_BLOCKED = "test_outbox_write_blocked";
    private static final String LABEL_WRITE_BLOCKED = "test_label_write_blocked";
    private static final String UNRELATED_LABEL_KEY = "team";
    private static final String UNRELATED_LABEL_VALUE = "platform";
    private static final String CONTRACT_ID = "txtest";
    private static final String VERSION = "1";
    private static final String STABLE_DATE = "2026-01-01";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    HandleFactory handles;

    @Inject
    ContractOutboxEventRecorder recorder;

    private String groupId;
    private String artifactId;
    private String prefix;
    private String statusKey;
    private String stableDateKey;
    private String ownerTeamKey;

    @BeforeEach
    void setUp() {
        groupId = ContractOutboxEventRecorder.GROUP_PREFIX + UUID.randomUUID();
        artifactId = "artifact-" + UUID.randomUUID();
        prefix = ContractLabels.contractPrefix(CONTRACT_ID);
        statusKey = prefix + ContractLabels.SUFFIX_STATUS;
        stableDateKey = prefix + ContractLabels.SUFFIX_STABLE_DATE;
        ownerTeamKey = prefix + ContractLabels.SUFFIX_OWNER_TEAM;
        createArtifact();
        // Cleared after creating the artifact so the ArtifactCreated event is not recorded.
        recorder.clear();
    }

    @AfterEach
    void tearDown() {
        // The injected failures are schema-wide, so they are always removed here as well as inline:
        // an assertion that fails between blocking and unblocking must not leak into the next test.
        unblockOutboxWrites();
        unblockStableDateLabelWrites();
        storage.deleteGlobalContractRuleset();
    }

    // ----------------------------------------------------------------------------------------------
    // Rollback on outbox-write failure
    // ----------------------------------------------------------------------------------------------

    @Test
    void testUpdateContractMetadataRollsBackWhenOutboxWriteFails() {
        storage.updateContractMetadata(groupId, artifactId, prefix, Map.of(ownerTeamKey, "before"));
        assertEquals("before", labels().get(ownerTeamKey));

        blockOutboxWritesFor(StorageEventType.CONTRACT_METADATA_UPDATED);
        assertRejectedByCheckConstraint(() -> storage.updateContractMetadata(groupId, artifactId, prefix,
                Map.of(ownerTeamKey, "after")));
        unblockOutboxWrites();

        Map<String, String> labels = labels();
        assertEquals("before", labels.get(ownerTeamKey));
        assertEquals(UNRELATED_LABEL_VALUE, labels.get(UNRELATED_LABEL_KEY));
    }

    @Test
    void testTransitionContractStatusRollsBackWhenOutboxWriteFails() {
        transitionTo(null, ContractStatus.DRAFT);
        assertEquals(ContractStatus.DRAFT.name(), labels().get(statusKey));

        blockOutboxWritesFor(StorageEventType.CONTRACT_STATUS_CHANGED);
        assertRejectedByCheckConstraint(() -> transitionTo(ContractStatus.DRAFT, ContractStatus.STABLE));
        unblockOutboxWrites();

        Map<String, String> labels = labels();
        assertEquals(ContractStatus.DRAFT.name(), labels.get(statusKey));
        assertNull(labels.get(stableDateKey));
        assertEquals(UNRELATED_LABEL_VALUE, labels.get(UNRELATED_LABEL_KEY));
    }

    @Test
    void testSetArtifactContractRulesetRollsBackWhenOutboxWriteFails() {
        storage.setArtifactContractRuleset(groupId, artifactId, ruleset("before"));

        blockOutboxWritesFor(StorageEventType.CONTRACT_RULESET_CONFIGURED);
        assertRejectedByCheckConstraint(
                () -> storage.setArtifactContractRuleset(groupId, artifactId, ruleset("after")));
        unblockOutboxWrites();

        assertSingleDomainRule("before", storage.getArtifactContractRuleset(groupId, artifactId));
    }

    @Test
    void testDeleteArtifactContractRulesetRollsBackWhenOutboxWriteFails() {
        storage.setArtifactContractRuleset(groupId, artifactId, ruleset("before"));

        blockOutboxWritesFor(StorageEventType.CONTRACT_RULESET_CONFIGURED);
        assertRejectedByCheckConstraint(() -> storage.deleteArtifactContractRuleset(groupId, artifactId));
        unblockOutboxWrites();

        assertSingleDomainRule("before", storage.getArtifactContractRuleset(groupId, artifactId));
    }

    @Test
    void testSetVersionContractRulesetRollsBackWhenOutboxWriteFails() {
        storage.setVersionContractRuleset(groupId, artifactId, VERSION, ruleset("before"));

        blockOutboxWritesFor(StorageEventType.CONTRACT_RULESET_CONFIGURED);
        assertRejectedByCheckConstraint(() -> storage.setVersionContractRuleset(groupId, artifactId, VERSION,
                ruleset("after")));
        unblockOutboxWrites();

        assertSingleDomainRule("before",
                storage.getVersionContractRuleset(groupId, artifactId, VERSION));
    }

    @Test
    void testDeleteVersionContractRulesetRollsBackWhenOutboxWriteFails() {
        storage.setVersionContractRuleset(groupId, artifactId, VERSION, ruleset("before"));

        blockOutboxWritesFor(StorageEventType.CONTRACT_RULESET_CONFIGURED);
        assertRejectedByCheckConstraint(
                () -> storage.deleteVersionContractRuleset(groupId, artifactId, VERSION));
        unblockOutboxWrites();

        assertSingleDomainRule("before",
                storage.getVersionContractRuleset(groupId, artifactId, VERSION));
    }

    @Test
    void testSetGlobalContractRulesetRollsBackWhenOutboxWriteFails() {
        storage.setGlobalContractRuleset(ruleset("before"));

        blockOutboxWritesFor(StorageEventType.CONTRACT_RULESET_CONFIGURED);
        assertRejectedByCheckConstraint(() -> storage.setGlobalContractRuleset(ruleset("after")));
        unblockOutboxWrites();

        assertSingleDomainRule("before", storage.getGlobalContractRuleset());
    }

    @Test
    void testDeleteGlobalContractRulesetRollsBackWhenOutboxWriteFails() {
        storage.setGlobalContractRuleset(ruleset("before"));

        blockOutboxWritesFor(StorageEventType.CONTRACT_RULESET_CONFIGURED);
        assertRejectedByCheckConstraint(() -> storage.deleteGlobalContractRuleset());
        unblockOutboxWrites();

        assertSingleDomainRule("before", storage.getGlobalContractRuleset());
    }

    // ----------------------------------------------------------------------------------------------
    // Rollback when the status transition itself fails part way through
    // ----------------------------------------------------------------------------------------------

    /**
     * The status label and the lifecycle date are written by two separate key-scoped merges. Blocking
     * only the lifecycle-date write fails the second merge after the first one has already written, which
     * must roll the status back as well.
     */
    @Test
    void testTransitionContractStatusRollsBackStatusWhenLifecycleDateWriteFails() {
        transitionTo(null, ContractStatus.DRAFT);

        blockStableDateLabelWrites();
        assertRejectedByCheckConstraint(() -> transitionTo(ContractStatus.DRAFT, ContractStatus.STABLE));
        unblockStableDateLabelWrites();

        Map<String, String> labels = labels();
        assertEquals(ContractStatus.DRAFT.name(), labels.get(statusKey));
        assertNull(labels.get(stableDateKey));
    }

    // ----------------------------------------------------------------------------------------------
    // Successful operations: unrelated labels survive and the expected payloads are emitted
    // ----------------------------------------------------------------------------------------------

    @Test
    void testSuccessfulMetadataUpdateKeepsUnrelatedLabelsAndPublishesEvent() {
        storage.updateContractMetadata(groupId, artifactId, prefix, Map.of(ownerTeamKey, "payments"));

        Map<String, String> labels = labels();
        assertEquals("payments", labels.get(ownerTeamKey));
        assertEquals(UNRELATED_LABEL_VALUE, labels.get(UNRELATED_LABEL_KEY));

        JSONObject payload = singlePayload(StorageEventType.CONTRACT_METADATA_UPDATED);
        assertEquals(groupId, payload.getString("groupId"));
        assertEquals(artifactId, payload.getString("artifactId"));
        assertEquals(StorageEventType.CONTRACT_METADATA_UPDATED.name(), payload.getString("eventType"));
    }

    @Test
    void testSuccessfulStatusTransitionWritesDateAndPublishesEvent() {
        transitionTo(null, ContractStatus.DRAFT);
        recorder.clear();

        transitionTo(ContractStatus.DRAFT, ContractStatus.STABLE);

        Map<String, String> labels = labels();
        assertEquals(ContractStatus.STABLE.name(), labels.get(statusKey));
        assertEquals(STABLE_DATE, labels.get(stableDateKey));
        assertEquals(UNRELATED_LABEL_VALUE, labels.get(UNRELATED_LABEL_KEY));

        JSONObject payload = singlePayload(StorageEventType.CONTRACT_STATUS_CHANGED);
        assertEquals(groupId, payload.getString("groupId"));
        assertEquals(artifactId, payload.getString("artifactId"));
        assertEquals(ContractStatus.DRAFT.name(), payload.getString("fromStatus"));
        assertEquals(ContractStatus.STABLE.name(), payload.getString("toStatus"));
    }

    @Test
    void testSuccessfulArtifactRulesetMutationsPublishScopedEvents() {
        storage.setArtifactContractRuleset(groupId, artifactId, ruleset("domain-rule"));

        ContractRuleSetDto stored = storage.getArtifactContractRuleset(groupId, artifactId);
        assertSingleDomainRule("domain-rule", stored);
        ContractRuleDto rule = stored.getDomainRules().get(0);
        assertEquals(RuleKind.CONDITION, rule.getKind());
        assertEquals(RuleMode.WRITE, rule.getMode());
        assertEquals("expression", rule.getType());
        assertEquals(RuleAction.ERROR, rule.getOnFailure());
        assertEquals(Set.of("contract"), rule.getTags());
        assertFalse(rule.isDisabled());

        JSONObject setPayload = singlePayload(StorageEventType.CONTRACT_RULESET_CONFIGURED);
        assertEquals(groupId, setPayload.getString("groupId"));
        assertEquals(artifactId, setPayload.getString("artifactId"));
        assertEquals(ContractRulesetConfigured.Action.SET.name(), setPayload.getString("action"));
        assertFalse(setPayload.has("version"), "An artifact-scoped event must not carry a version");

        recorder.clear();
        storage.deleteArtifactContractRuleset(groupId, artifactId);

        assertTrue(storage.getArtifactContractRuleset(groupId, artifactId).getDomainRules().isEmpty());
        JSONObject deletePayload = singlePayload(StorageEventType.CONTRACT_RULESET_CONFIGURED);
        assertEquals(ContractRulesetConfigured.Action.DELETE.name(), deletePayload.getString("action"));
    }

    @Test
    void testSuccessfulVersionRulesetMutationPublishesVersionScopedEvent() {
        storage.setVersionContractRuleset(groupId, artifactId, VERSION, ruleset("version-rule"));

        assertSingleDomainRule("version-rule",
                storage.getVersionContractRuleset(groupId, artifactId, VERSION));

        JSONObject payload = singlePayload(StorageEventType.CONTRACT_RULESET_CONFIGURED);
        assertEquals(groupId, payload.getString("groupId"));
        assertEquals(artifactId, payload.getString("artifactId"));
        assertEquals(VERSION, payload.getString("version"));
        assertEquals(ContractRulesetConfigured.Action.SET.name(), payload.getString("action"));
    }

    @Test
    void testSuccessfulGlobalRulesetMutationPublishesGlobalScopedEvent() {
        storage.setGlobalContractRuleset(ruleset("global-rule"));

        assertSingleDomainRule("global-rule", storage.getGlobalContractRuleset());

        JSONObject payload = singlePayload(StorageEventType.CONTRACT_RULESET_CONFIGURED);
        assertEquals(ContractRulesetConfigured.GLOBAL_COORDINATE, payload.getString("groupId"));
        assertEquals(ContractRulesetConfigured.GLOBAL_COORDINATE, payload.getString("artifactId"));
        assertEquals(ContractRulesetConfigured.Action.SET.name(), payload.getString("action"));
    }

    // ----------------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------------

    private void createArtifact() {
        storage.createArtifact(groupId, artifactId, ArtifactType.JSON,
                EditableArtifactMetaDataDto.builder()
                        .labels(Map.of(UNRELATED_LABEL_KEY, UNRELATED_LABEL_VALUE)).build(),
                VERSION,
                ContentWrapperDto.builder().content(ContentHandle.create("{\"type\":\"object\"}"))
                        .contentType("application/json").references(List.of()).build(),
                EditableVersionMetaDataDto.builder().build(), List.of(), false, false, "test");
    }

    private void transitionTo(ContractStatus from, ContractStatus to) {
        storage.transitionContractStatus(groupId, artifactId, from == null ? null : from.name(), to.name(),
                prefix, STABLE_DATE);
    }

    private Map<String, String> labels() {
        return storage.getArtifactMetaData(groupId, artifactId).getLabels();
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

    private JSONObject singlePayload(StorageEventType type) {
        List<OutboxEvent> events = recorder.eventsOfType(type.name());
        assertEquals(1, events.size(), "Expected exactly one " + type + " event, found " + events);
        return events.get(0).getPayload();
    }

    /**
     * Asserts that the operation failed because the database rejected one of its writes with a
     * check-constraint violation. The exception travels from the JDBC layer through the handle factory, a
     * synchronous CDI observer and the storage interceptors, so the assertion is made on the SQL state in
     * the cause chain rather than on whichever wrapper type happens to surface.
     */
    private static void assertRejectedByCheckConstraint(Executable operation) {
        Throwable thrown = assertThrows(RuntimeException.class, operation);
        SQLException cause = findSqlException(thrown);
        assertNotNull(cause, "Expected a SQLException in the cause chain, but got: " + thrown);
        assertEquals(CHECK_VIOLATION_SQL_STATE, cause.getSQLState(),
                "Expected a check-constraint violation, but got: " + cause);
    }

    private static SQLException findSqlException(Throwable thrown) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
        }
        return null;
    }

    /**
     * Makes the outbox INSERT fail for one event type only.
     * <p>
     * Scoping by type is essential rather than cosmetic. The label merge used by
     * {@code updateContractMetadata} and {@code transitionContractStatus} goes through
     * {@code SqlArtifactRepository.updateArtifactMetaData}, which writes its own
     * {@code ARTIFACT_METADATA_UPDATED} outbox row inside the merge's own handle scope. Blocking every
     * outbox write would therefore fail the merge itself, and the data would roll back even without a
     * shared transaction around the contract event — making the assertions pass for the wrong reason.
     * Failing only the contract event reproduces the real defect: the data write succeeds and commits,
     * and the outbox write that is supposed to accompany it is the thing that fails.
     * </p>
     * <p>
     * {@code NOT VALID} skips validation of existing rows so the constraint can be added regardless of
     * what else is in the table.
     * </p>
     */
    private void blockOutboxWritesFor(StorageEventType eventType) {
        unblockOutboxWrites();
        executeDdl("ALTER TABLE outbox ADD CONSTRAINT " + OUTBOX_BLOCKED + " CHECK (\"type\" <> '"
                + eventType.name() + "') NOT VALID");
    }

    private void unblockOutboxWrites() {
        executeDdl("ALTER TABLE outbox DROP CONSTRAINT IF EXISTS " + OUTBOX_BLOCKED);
    }

    /**
     * Makes only the lifecycle stable-date label row fail to insert, leaving the status label write (and
     * every other label) working.
     */
    private void blockStableDateLabelWrites() {
        unblockStableDateLabelWrites();
        executeDdl("ALTER TABLE artifact_labels ADD CONSTRAINT " + LABEL_WRITE_BLOCKED
                + " CHECK (labelKey NOT LIKE '%" + ContractLabels.SUFFIX_STABLE_DATE + "') NOT VALID");
    }

    private void unblockStableDateLabelWrites() {
        executeDdl("ALTER TABLE artifact_labels DROP CONSTRAINT IF EXISTS " + LABEL_WRITE_BLOCKED);
    }

    private void executeDdl(String ddl) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(ddl).execute();
            return null;
        });
    }
}
