package io.apicurio.registry.content;

import io.apicurio.registry.utils.IoBufferedInputStream;
import io.apicurio.registry.utils.IoUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


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
        return new IoBufferedInputStream(is, (bytes, count) -> {
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

    @Override
    public String content() {
        if (content == null) {
            content = new String(bytes(), StandardCharsets.UTF_8);
        }
        return content;
    }
}
