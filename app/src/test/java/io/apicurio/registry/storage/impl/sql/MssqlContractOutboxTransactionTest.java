package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.util.MssqlTestProfile;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Runs {@link AbstractContractOutboxTransactionTest} against SQL Server, the other dialect for which
 * {@code SqlEventRepository.supportsDatabaseEvents()} is true and outbox rows are therefore written.
 * <p>
 * Disabled on macOS for the same reason as {@link MssqlStorageTest}: the SQL Server container the test
 * resource starts does not run there.
 * </p>
 */
@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(MssqlTestProfile.class)
@Typed(MssqlContractOutboxTransactionTest.class)
@DisabledOnOs(OS.MAC)
public class MssqlContractOutboxTransactionTest extends AbstractContractOutboxTransactionTest {

    @Override
    protected String checkViolationSqlState() {
        return "23000";
    }

    @Override
    protected String addUnvalidatedCheckConstraint(String table, String constraint, String condition) {
        return "ALTER TABLE " + table + " WITH NOCHECK ADD CONSTRAINT " + constraint + " CHECK ("
                + condition + ")";
    }
}
