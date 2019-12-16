package io.apicurio.registry.utils.serde.strategy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Handle artifact id in the msg bytes.
 *
 * @author Ales Justin
 */
public interface IdHandler {
    void writeId(long id, OutputStream out) throws IOException;

    void writeId(long id, ByteBuffer buffer);

    long readId(ByteBuffer buffer);

    int idSize();
}
