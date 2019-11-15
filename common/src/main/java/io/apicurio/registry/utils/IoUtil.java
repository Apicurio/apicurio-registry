package io.apicurio.registry.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Ales Justin
 */
public class IoUtil {

    private static ByteArrayOutputStream toBaos(InputStream stream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = stream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result;
    }

    public static byte[] toBytes(InputStream stream) {
        try {
            return toBaos(stream).toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String toString(InputStream stream) {
        try {
            return toBaos(stream).toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static long copy(InputStream input, OutputStream output) throws IOException {
        final byte[] buffer = new byte[8192];
        int n;
        long count = 0;
        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

}
