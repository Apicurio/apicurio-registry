package io.apicurio.registry.content;

import java.io.InputStream;

public interface ContentHandle {

    static ContentHandle create(InputStream stream) {
        return new StreamContentHandle(stream);
    }

    static ContentHandle create(byte[] bytes) {
        return new BytesContentHandle(bytes);
    }

    static ContentHandle create(String content) {
        return new StringContentHandle(content);
    }

    InputStream stream();

    byte[] bytes();

    String content();

    int getSizeBytes();

    String getSha256Hash();

    /**
     * Uses the {@link ContentHandle#bytes()} method to compare the content.
     */
    boolean equals(Object other);

    /**
     * Uses the {@link ContentHandle#bytes()} method to compute the hash code of the content.
     */
    int hashCode();

    /**
     * Returns a string representation of the content using the {@link ContentHandle#content()} method.
     */
    String toString();
}
