package io.apicurio.common.apps.content.handle;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author Ales Justin
 */
abstract class AbstractContentHandle implements ContentHandle {

    protected byte[] bytes;
    protected String string;

    @Override
    public InputStream stream() {
        return new ByteArrayInputStream(bytes());
    }

    @Override
    public String string() {
        if (string == null) {
            string = new String(bytes(), StandardCharsets.UTF_8);
        }
        return string;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ContentHandle))
            return false;
        ContentHandle that = (ContentHandle) o;
        return Arrays.equals(bytes(), that.bytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes());
    }

    @Override
    public int getSizeBytes() {
        return bytes().length;
    }

    @Override
    public String getSha256Hash() {
        return DigestUtils.sha256Hex(bytes());
    }
}
