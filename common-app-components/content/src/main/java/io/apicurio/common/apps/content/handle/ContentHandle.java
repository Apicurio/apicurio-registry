package io.apicurio.common.apps.content.handle;

import java.io.InputStream;

/**
 * @author Ales Justin
 */
public interface ContentHandle {

    static ContentHandle create(InputStream stream) {
        return new StreamContentHandle(stream);
    }

    static ContentHandle create(byte[] bytes) {
        return new BytesContentHandle(bytes);
    }

    static ContentHandle create(String string) {
        return new StringContentHandle(string);
    }

    InputStream stream();

    byte[] bytes();

    String string();

    int getSizeBytes();

    String getSha256Hash();
}
