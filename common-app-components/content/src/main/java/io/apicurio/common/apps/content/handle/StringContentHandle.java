package io.apicurio.common.apps.content.handle;

import java.nio.charset.StandardCharsets;

/**
 * @author Ales Justin
 */
class StringContentHandle extends AbstractContentHandle {

    StringContentHandle(String string) {
        this.string = string;
    }

    @Override
    public byte[] bytes() {
        if (bytes == null) {
            bytes = string.getBytes(StandardCharsets.UTF_8);
        }
        return bytes;
    }

    @Override
    public String string() {
        return string;
    }
}
