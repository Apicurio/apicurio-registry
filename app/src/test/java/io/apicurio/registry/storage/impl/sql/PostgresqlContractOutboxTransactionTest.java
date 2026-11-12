package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Tag;

/**
 * Runs {@link AbstractContractOutboxTransactionTest} against PostgreSQL.
 */
@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(PostgresqlTestProfile.class)
@Typed(PostgresqlContractOutboxTransactionTest.class)
public class PostgresqlContractOutboxTransactionTest extends AbstractContractOutboxTransactionTest {

    @Override
    protected String checkViolationSqlState() {
        return "23514";
    }

    @Override
    protected String addUnvalidatedCheckConstraint(String table, String constraint, String condition) {
        return "ALTER TABLE " + table + " ADD CONSTRAINT " + constraint + " CHECK (" + condition
                + ") NOT VALID";
    }
}
