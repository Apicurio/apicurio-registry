package io.apicurio.common.apps.content.handle;

import io.apicurio.common.apps.content.IoUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiConsumer;

/**
 * @author Ales Justin
 */
class StreamContentHandle extends AbstractContentHandle {
    private InputStream stream;

    StreamContentHandle(InputStream stream) {
        this.stream = stream;
    }

    @Override
    public InputStream stream() {
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        InputStream is = stream;
        stream = null;
        return new OnCloseBufferedInputStream(is, (bytes, count) -> {
            byte[] copy = new byte[count];
            System.arraycopy(bytes, 0, copy, 0, count);
            this.bytes = copy;
        });
    }

    @Override
    public byte[] bytes() {
        if (bytes == null) {
            InputStream is = stream;
            stream = null;
            bytes = IoUtil.toBytes(is);
        }
        return bytes;
    }

    /**
     * @author Ales Justin
     */
    private static class OnCloseBufferedInputStream extends BufferedInputStream {
        private final BiConsumer<byte[], Integer> onClose;

        private OnCloseBufferedInputStream(InputStream in, BiConsumer<byte[], Integer> onClose) {
            super(in);
            this.onClose = onClose;
        }

        @Override
        public void close() throws IOException {
            onClose.accept(buf, count);
            super.close();
        }
    }
}
