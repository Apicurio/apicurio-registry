package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.config.IdOption;

import java.nio.ByteBuffer;

/**
 * IdHandler that tries to read a 4-byte ID, but if it sees all zeros in those 4 bytes,
 * it assumes it was actually an 8-byte ID and reads the full 8 bytes.
 * <p>
 * This handler is optimistic in that it makes the following assumptions on schema IDs (global or content):
 * <ul>
 *     <li>Schema IDs are strictly positive (non-zero)</li>
 *     <li>Schema IDs are less than or equal to the max value of a signed 4-byte integer</li>
 * </ul>
 * <p>
 * This is useful during a migration from Apicurio Registry v2 to v3,
 * deserializing data that may have been written by either a 4-byte (default) or 8-byte (legacy) ID handler.
 */
public class OptimisticFallbackIdHandler extends Default4ByteIdHandler {

    @Override
    public ArtifactReference readId(ByteBuffer buffer) {
        // Peek at the first 4 bytes
        int firstFour = buffer.getInt();

        // If we see 0 and there's enough room, we assume it was an 8-byte write
        if (firstFour == 0 && buffer.remaining() >= 4) {
            buffer.position(buffer.position() - 4);
            if (idOption == IdOption.globalId) {
                return ArtifactReference.builder().globalId(buffer.getLong()).build();
            } else {
                return ArtifactReference.builder().contentId(buffer.getLong()).build();
            }
        }

        // Otherwise, the first 4 bytes were the ID
        if (idOption == IdOption.globalId) {
            return ArtifactReference.builder().globalId((long) firstFour).build();
        } else {
            return ArtifactReference.builder().contentId((long) firstFour).build();
        }
    }

    @Override
    public int idSize(ArtifactReference reference, ByteBuffer buffer) {
        return buffer.position() - 1;
    }
}