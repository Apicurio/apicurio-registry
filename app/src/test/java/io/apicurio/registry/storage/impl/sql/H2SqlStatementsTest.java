package io.apicurio.registry.storage.impl.sql;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class H2SqlStatementsTest {

    @Test
    public void testSnapshotCompression() {
        H2SqlStatements statements = new H2SqlStatements();

        // Write tests
        Assertions.assertTrue(statements.createDataSnapshot("foo.sql.gz").contains("COMPRESSION GZIP"));
        Assertions.assertFalse(statements.createDataSnapshot("foo.sql").contains("COMPRESSION GZIP"));

        // Read/Restore tests
        Assertions.assertTrue(statements.restoreFromSnapshot("foo.sql.gz").contains("COMPRESSION GZIP"));
        Assertions.assertFalse(statements.restoreFromSnapshot("foo.sql").contains("COMPRESSION GZIP"));
    }
}
