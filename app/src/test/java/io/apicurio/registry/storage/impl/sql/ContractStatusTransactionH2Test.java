package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies on H2 that {@code transitionContractStatus} rolls the status label back when the
 * lifecycle-date write fails, complementing {@link AbstractContractOutboxTransactionTest}, which covers
 * the same storage code on the dialects that write outbox rows.
 * <p>
 * The dialect is the reason this test exists separately. On H2
 * {@code SqlEventRepository.supportsDatabaseEvents()} is false, so {@code SqlEventsProcessor}
 * short-circuits and the transaction contains no outbox INSERT at all; what is under test here is the
 * atomicity of the status and lifecycle-date label writes on their own. H2 is also the dialect the
 * KafkaSQL tests run their SQL store on, so this covers the storage code as the journal applies it.
 * </p>
 * <p>
 * The failure is injected into the real database, but through a CHECK constraint scoped to a single
 * label value rather than to the column: H2 has no {@code NOT VALID}, so the constraint is validated
 * against existing rows as it is added, and a sentinel value no other test writes cannot collide with
 * them. Scoping it to that value also keeps the injected failure from reaching any other test sharing
 * this profile.
 * </p>
 * <p>
 * Confirmed to fail against the unwrapped storage code, where the status merge commits on its own and
 * leaves the artifact STABLE with no stable-date label.
 * </p>
 */
@QuarkusTest
public class ContractStatusTransactionH2Test {

    /** SQL state class for an integrity-constraint violation; 23513 on H2, 23514 on PostgreSQL. */
    private static final String INTEGRITY_VIOLATION_SQL_STATE_CLASS = "23";

    private static final String LABEL_VALUE_BLOCKED = "test_label_value_blocked";
    private static final String BLOCKED_DATE_VALUE = "contract-rollback-sentinel-date";
    private static final String UNRELATED_LABEL_KEY = "team";
    private static final String UNRELATED_LABEL_VALUE = "platform";
    private static final String CONTRACT_ID = "h2txtest";
    private static final String VERSION = "1";
    private static final String STABLE_DATE = "2026-01-01";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    HandleFactory handles;

    private String groupId;
    private String artifactId;
    private String prefix;
    private String statusKey;
    private String stableDateKey;

    @BeforeEach
    void setUp() {
        groupId = "contract-h2-tx-" + UUID.randomUUID();
        artifactId = "artifact-" + UUID.randomUUID();
        prefix = ContractLabels.contractPrefix(CONTRACT_ID);
        statusKey = prefix + ContractLabels.SUFFIX_STATUS;
        stableDateKey = prefix + ContractLabels.SUFFIX_STABLE_DATE;
        storage.createArtifact(groupId, artifactId, ArtifactType.JSON,
                EditableArtifactMetaDataDto.builder()
                        .labels(Map.of(UNRELATED_LABEL_KEY, UNRELATED_LABEL_VALUE)).build(),
                VERSION,
                ContentWrapperDto.builder().content(ContentHandle.create("{\"type\":\"object\"}"))
                        .contentType("application/json").references(List.of()).build(),
                EditableVersionMetaDataDto.builder().build(), List.of(), false, false, "test");
    }

    @AfterEach
    void tearDown() {
        // The constraint is schema-wide, so it is dropped here as well as inline: an assertion that
        // fails while it is in place must not leak it into another test. The artifact goes too, since
        // this test shares its application with the rest of the default profile.
        unblockStableDateValueWrites();
        storage.deleteArtifact(groupId, artifactId);
    }

    @Test
    void testTransitionRollsBackStatusWhenLifecycleDateWriteFails() {
        transitionTo(null, ContractStatus.DRAFT, STABLE_DATE);
        assertEquals(ContractStatus.DRAFT.name(), labels().get(statusKey));

        blockStableDateValueWrites();
        try {
            assertRejectedByIntegrityConstraint(
                    () -> transitionTo(ContractStatus.DRAFT, ContractStatus.STABLE, BLOCKED_DATE_VALUE));
        } finally {
            unblockStableDateValueWrites();
        }

        Map<String, String> labels = labels();
        assertEquals(ContractStatus.DRAFT.name(), labels.get(statusKey),
                "The status label must roll back together with the failed lifecycle-date write");
        assertNull(labels.get(stableDateKey), "No lifecycle date may survive the rolled-back transition");
        assertEquals(UNRELATED_LABEL_VALUE, labels.get(UNRELATED_LABEL_KEY),
                "The rollback must not disturb labels outside the contract prefix");
    }

    @Test
    void testTransitionWritesStatusAndLifecycleDateWhenNothingFails() {
        transitionTo(null, ContractStatus.DRAFT, STABLE_DATE);
        transitionTo(ContractStatus.DRAFT, ContractStatus.STABLE, STABLE_DATE);

        Map<String, String> labels = labels();
        assertEquals(ContractStatus.STABLE.name(), labels.get(statusKey));
        assertEquals(STABLE_DATE, labels.get(stableDateKey));
        assertEquals(UNRELATED_LABEL_VALUE, labels.get(UNRELATED_LABEL_KEY));
    }

    private void transitionTo(ContractStatus from, ContractStatus to, String effectiveDate) {
        storage.transitionContractStatus(groupId, artifactId, from == null ? null : from.name(), to.name(),
                prefix, effectiveDate);
    }

    private Map<String, String> labels() {
        return storage.getArtifactMetaData(groupId, artifactId).getLabels();
    }

    /**
     * Fails the INSERT of one specific label value, which is how the lifecycle-date write is made to fail
     * where it really happens, inside the production label merge.
     */
    private void blockStableDateValueWrites() {
        unblockStableDateValueWrites();
        executeDdl("ALTER TABLE artifact_labels ADD CONSTRAINT " + LABEL_VALUE_BLOCKED
                + " CHECK (labelValue <> '" + BLOCKED_DATE_VALUE + "')");
    }

    private void unblockStableDateValueWrites() {
        executeDdl("ALTER TABLE artifact_labels DROP CONSTRAINT IF EXISTS " + LABEL_VALUE_BLOCKED);
    }

    private void executeDdl(String ddl) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(ddl).execute();
        });
    }

    private static void assertRejectedByIntegrityConstraint(Executable operation) {
        Throwable thrown = assertThrows(RuntimeException.class, operation);
        SQLException cause = findSqlException(thrown);
        assertNotNull(cause, "Expected a SQLException in the cause chain, but got: " + thrown);
        assertNotNull(cause.getSQLState(), "Expected a SQL state on: " + cause);
        assertTrue(cause.getSQLState().startsWith(INTEGRITY_VIOLATION_SQL_STATE_CLASS),
                "Expected an integrity-constraint violation, but got: " + cause);
    }

    private static SQLException findSqlException(Throwable thrown) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
        }
        return null;
    }
}
