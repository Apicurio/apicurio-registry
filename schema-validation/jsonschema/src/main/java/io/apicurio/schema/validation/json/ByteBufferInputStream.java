package io.apicurio.schema.validation.json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class ByteBufferInputStream extends InputStream {
    private final ByteBuffer buffer;

    public ByteBufferInputStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public int read() {
        return !this.buffer.hasRemaining() ? -1 : this.buffer.get() & 255;
    }

    public int read(byte[] bytes, int off, int len) {
        if (len == 0) {
            return 0;
        } else if (!this.buffer.hasRemaining()) {
            return -1;
        } else {
            len = Math.min(len, this.buffer.remaining());
            this.buffer.get(bytes, off, len);
            return len;
        }
    }

    public int available() throws IOException {
        return this.buffer.remaining();
    }
}
