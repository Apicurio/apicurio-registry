package io.apicurio.registry.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
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
        copy(stream, result);
        return result;
    }

    /**
     * Close closeable, unchecked IOException is thrown for any IO exception.
     *
     * @param closeable the closeable
     */
    public static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Get byte array from stream.
     * Stream is closed at the end.
     *
     * @param stream the stream
     * @return stream as a byte array
     */
    public static byte[] toBytes(InputStream stream) {
        try {
            return toBaos(stream).toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            close(stream);
        }
    }

    /**
     * Get string from stream.
     * Stream is closed at the end.
     *
     * @param stream the stream
     * @return stream as a string
     */
    public static String toString(InputStream stream) {
        try {
            return toBaos(stream).toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            close(stream);
        }
    }

    /**
     * Get stream from content.
     *
     * @param content the content
     * @return content as stream
     */
    public static InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
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
