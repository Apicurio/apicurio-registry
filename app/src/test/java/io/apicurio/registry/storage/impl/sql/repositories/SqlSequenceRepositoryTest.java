package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.impl.sql.H2SqlStatements;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Regression for #7585: H2 sequence counters must be per repository instance so
 * multiple storages in one JVM do not overwrite each other's IDs.
 */
public class SqlSequenceRepositoryTest {

    @Test
    public void sequenceCountersAreIsolatedBetweenInstances() {
        SqlSequenceRepository blue = new SqlSequenceRepository(null, new H2SqlStatements(),
                LoggerFactory.getLogger("blue"));
        SqlSequenceRepository green = new SqlSequenceRepository(null, new H2SqlStatements(),
                LoggerFactory.getLogger("green"));

        // Advance blue's counters
        assertEquals(1L, blue.nextGlobalIdRaw(null));
        assertEquals(2L, blue.nextGlobalIdRaw(null));
        assertEquals(1L, blue.nextContentIdRaw(null));

        // Green must start from its own counters, not continue from blue
        assertEquals(1L, green.nextGlobalIdRaw(null));
        assertEquals(1L, green.nextContentIdRaw(null));

        // Blue continues independently
        assertEquals(3L, blue.nextGlobalIdRaw(null));
        assertNotEquals(blue.nextGlobalIdRaw(null), green.nextGlobalIdRaw(null));
    }
}
