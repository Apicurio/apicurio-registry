package io.apicurio.registry.utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiConsumer;

// TODO This does not work with com.fasterxml.jackson.databind.ObjectMapper.readTree(java.io.InputStream)
// because count = 0 at the end, even if there is valid data in buf. This causes the content handle to become empty.
// Needs more investigation, but io.apicurio.registry.content.StreamContentHandle.bytes() can be called as a workaround.
// I suspect it's caused by Jackson manipulating the stream in unusual ways (reset?).
public class IoBufferedInputStream extends BufferedInputStream {
    private final BiConsumer<byte[], Integer> onClose;

    public IoBufferedInputStream(InputStream in, BiConsumer<byte[], Integer> onClose) {
        super(in);
        this.onClose = onClose;
    }

    @Override
    public void close() throws IOException {
        onClose.accept(buf, count);
        super.close();
    }


}
