package io.apicurio.registry.content;

import java.nio.charset.StandardCharsets;


class BytesContentHandle extends AbstractContentHandle {

    BytesContentHandle(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public byte[] bytes() {
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
