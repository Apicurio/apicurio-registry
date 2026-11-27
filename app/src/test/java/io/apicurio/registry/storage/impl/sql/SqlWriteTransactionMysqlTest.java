package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.util.MysqlTestProfile;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(MysqlTestProfile.class)
@Typed(SqlWriteTransactionMysqlTest.class)
public class SqlWriteTransactionMysqlTest extends SqlWriteTransactionTest {

    @Override
    @Test
    @Disabled("Concurrent creates in one new group with the same content deadlock on InnoDB; also before #11412")
    void concurrentCreatesInANewGroupWithTheSameContentAllSucceed() {
    }
}
