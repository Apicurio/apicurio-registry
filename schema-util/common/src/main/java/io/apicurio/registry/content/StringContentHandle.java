package io.apicurio.registry.content;

import java.nio.charset.StandardCharsets;


class StringContentHandle extends AbstractContentHandle {

    StringContentHandle(String content) {
        this.content = content;
    }

    @Override
    public byte[] bytes() {
        if (bytes == null) {
            bytes = content.getBytes(StandardCharsets.UTF_8);
        }
        return bytes;
    }

    @Override
    public String content() {
        return content;
    }
}
