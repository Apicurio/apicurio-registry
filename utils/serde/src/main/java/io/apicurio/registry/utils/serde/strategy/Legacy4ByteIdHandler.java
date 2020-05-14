package io.apicurio.registry.utils.serde.strategy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * IdHandler that assumes 4 bytes for the magic number (the ID).
 *
 * @author Ales Justin
 */
public class Legacy4ByteIdHandler implements IdHandler {
    static final int idSize = 4; // e.g. Confluent uses 4 / int

    public void writeId(long id, OutputStream out) throws IOException {
        out.write(ByteBuffer.allocate(idSize).putInt((int) id).array());
    }

    @Override
    public void writeId(long id, ByteBuffer buffer) {
        buffer.putInt((int) id);
    }

    public long readId(ByteBuffer buffer) {
        return buffer.getInt();
    }

    @Override
    public int idSize() {
        return idSize;
    }
}
