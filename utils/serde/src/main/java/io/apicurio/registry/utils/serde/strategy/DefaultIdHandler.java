package io.apicurio.registry.utils.serde.strategy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * @author Ales Justin
 */
public class DefaultIdHandler implements IdHandler {
    static final int idSize = 8; // we use 8 / long

    public void writeId(long id, OutputStream out) throws IOException {
        out.write(ByteBuffer.allocate(idSize).putLong(id).array());
    }

    public void writeId(long id, ByteBuffer buffer) {
        buffer.putLong(id);
    }

    public long readId(ByteBuffer buffer) {
        return buffer.getLong();
    }

    @Override
    public int idSize() {
        return idSize;
    }
}
