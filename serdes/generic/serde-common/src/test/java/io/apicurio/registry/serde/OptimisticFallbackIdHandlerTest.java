package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class OptimisticFallbackIdHandlerTest {

    private OptimisticFallbackIdHandler handler;

    @BeforeEach
    public void setup() {
        handler = new OptimisticFallbackIdHandler();
        Map<String, Object> configs = new HashMap<>();
        // Configure to use Global ID for these tests
        configs.put("apicurio.registry.use-id", "globalId");
        handler.configure(configs, false);
    }

    @Test
    public void testReadNew4ByteId() {
        // GIVEN: A 4-byte ID written (1234)
        int idValue = 1234;
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(idValue);
        buffer.flip();

        // WHEN: Reading the ID
        ArtifactReference ref = handler.readId(buffer);

        // THEN: Position should be at the end, and ID should match
        Assertions.assertEquals(idValue, ref.getGlobalId());
        Assertions.assertEquals(4, buffer.position(), "Should have consumed 4 bytes");
    }

    @Test
    public void testReadLegacy8ByteSmallId() {
        // GIVEN: An 8-byte legacy ID (1234) -> [0,0,0,0, 0,0,4,D2]
        long idValue = 1234L;
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(idValue);
        buffer.flip();

        // WHEN: Reading the ID
        ArtifactReference ref = handler.readId(buffer);

        // THEN: Should detect the zeros and consume all 8 bytes
        Assertions.assertEquals(idValue, ref.getGlobalId());
        Assertions.assertEquals(8, buffer.position(), "Should have consumed all 8 bytes of legacy format");
    }

    @Test
    public void testReadLegacy8ByteLargeId() {
        // GIVEN: A legacy ID larger than Integer.MAX_VALUE
        // This tests the branch where firstFour != 0
        long largeId = Integer.MAX_VALUE + 100L;
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(largeId);
        buffer.flip();

        // WHEN: Reading the ID
        ArtifactReference ref = handler.readId(buffer);

        // THEN: It should still read correctly because buffer.getLong(pos-4)
        // effectively captures the full 8 bytes starting from the beginning.
        Assertions.assertEquals(largeId, ref.getGlobalId());
        Assertions.assertEquals(8, buffer.position());
    }

    @Test
    public void testDynamicIdSize() {
        // GIVEN: A small ID
        ByteBuffer smallId = ByteBuffer.allocate(5).put((byte) 0x00).putInt(500).flip();
        smallId.get();
        smallId.getInt();
        ArtifactReference smallRef = ArtifactReference.builder().globalId(500L).build();
        // GIVEN: A large ID
        ByteBuffer largeId = ByteBuffer.allocate(9).put((byte) 0x00).putLong(3000000000L).flip();
        largeId.get();
        largeId.getLong();
        ArtifactReference largeRef = ArtifactReference.builder().globalId(3000000000L).build();

        // THEN: Sizes should vary (Note: This assumes your SPI supports this method)
        Assertions.assertEquals(4, handler.idSize(smallRef, smallId));
        Assertions.assertEquals(8, handler.idSize(largeRef, largeId));
    }
}