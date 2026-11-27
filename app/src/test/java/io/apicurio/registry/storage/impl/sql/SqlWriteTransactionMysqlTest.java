package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.util.MysqlTestProfile;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.SQLTransactionRollbackException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(MysqlTestProfile.class)
@Typed(SqlWriteTransactionMysqlTest.class)
public class SqlWriteTransactionMysqlTest extends SqlWriteTransactionTest {

    @Override
    @Test
    @Disabled("Concurrent creates in one new group with the same content deadlock on InnoDB; also before #11412, see #11547")
    void concurrentCreatesInANewGroupWithTheSameContentAllSucceed() {
    }

    /**
     * On MySQL the same race can deadlock (as it can without units of work). InnoDB then rolls back the
     * whole transaction, so a unit hit by the deadlock must fail as a whole and leave none of its writes,
     * while the other units commit.
     */
    @Override
    @Test
    void concurrentCreatesOfTheSameArtifactInUnitsOfWorkKeepTheLosersUsable() throws Exception {
        String groupId = uniqueId("uow-race");
        String artifactId = "shared";
        ContentWrapperDto content = avro(groupId);

        List<Throwable> outcomes = runConcurrently(THREADS, i -> storage.inUnitOfWork(() -> {
            try {
                create(groupId, artifactId, content, false);
            } catch (ArtifactAlreadyExistsException e) {
                assertEquals(artifactId, storage.getArtifactMetaData(groupId, artifactId).getArtifactId());
            }
            create(groupId, "own" + i, avro(groupId + i), false);
            return null;
        }));

        int committed = 0;
        for (int i = 0; i < THREADS; i++) {
            int index = i;
            boolean exists = onOtherThread(() -> storage.isArtifactExists(groupId, "own" + index));
            if (outcomes.get(i) == null) {
                committed++;
                assertTrue(exists, "unit " + i + " committed but its write is missing");
            } else {
                assertTrue(isDeadlock(outcomes.get(i)), "unit " + i + " failed with " + outcomes.get(i));
                assertFalse(exists, "unit " + i + " failed but its write was committed");
            }
        }
        assertTrue(committed >= 1, "outcomes: " + outcomes);
        assertEquals(1L, (long) onOtherThread(() -> storage.countArtifactVersions(groupId, artifactId)));
    }

    private static boolean isDeadlock(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof SQLTransactionRollbackException) {
                return true;
            }
        }
        return false;
    }
}
