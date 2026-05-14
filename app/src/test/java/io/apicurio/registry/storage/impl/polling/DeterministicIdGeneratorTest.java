package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.content.ContentHandle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicIdGeneratorTest {

    @Test
    void generatedIdsAreStableAndInRange() {
        // Same inputs produce same outputs
        long id1 = DeterministicIdGenerator.contentId(ContentHandle.create("hello"));
        long id2 = DeterministicIdGenerator.contentId(ContentHandle.create("hello"));
        assertEquals(id1, id2);

        long gid1 = DeterministicIdGenerator.globalId("g", "a", "1");
        long gid2 = DeterministicIdGenerator.globalId("g", "a", "1");
        assertEquals(gid1, gid2);

        // Different inputs produce different outputs
        long id3 = DeterministicIdGenerator.contentId(ContentHandle.create("world"));
        assertNotEquals(id1, id3);

        long gid3 = DeterministicIdGenerator.globalId("g", "a", "2");
        assertNotEquals(gid1, gid3);

        // All IDs are in the upper half of positive longs
        for (long id : new long[]{id1, id3, gid1, gid3}) {
            assertTrue(id >= DeterministicIdGenerator.GENERATED_ID_MIN,
                    "ID " + id + " should be >= " + DeterministicIdGenerator.GENERATED_ID_MIN);
            assertTrue(id > 0, "ID should be positive");
        }
    }
}
