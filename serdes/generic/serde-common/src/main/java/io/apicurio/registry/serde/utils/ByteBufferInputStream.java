/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Adapted from Apache Kafka's ByteBufferInputStream
 * Original source: org.apache.kafka.common.utils.ByteBufferInputStream
 * Available since Kafka 3.6.0
 */
package io.apicurio.registry.serde.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * A byte buffer backed input stream that allows reading from a ByteBuffer without copying data.
 * This implementation wraps a ByteBuffer and provides InputStream operations directly on the buffer,
 * avoiding the memory overhead of copying buffer contents to a byte array.
 */
public final class ByteBufferInputStream extends InputStream {

    private final ByteBuffer buffer;

    /**
     * Creates a new ByteBufferInputStream that reads from the given buffer.
     * The stream will read from the buffer's current position to its limit.
     *
     * @param buffer the ByteBuffer to read from
     */
    public ByteBufferInputStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Reads the next byte of data from the input stream.
     *
     * @return the next byte of data, or -1 if the end of the stream is reached
     */
    public int read() {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        return buffer.get() & 0xFF;
    }

    /**
     * Reads up to len bytes of data from the input stream into an array of bytes.
     *
     * @param bytes the buffer into which the data is read
     * @param off the start offset in array bytes at which the data is written
     * @param len the maximum number of bytes to read
     * @return the total number of bytes read into the buffer, or -1 if there is no more data
     */
    public int read(byte[] bytes, int off, int len) {
        if (len == 0) {
            return 0;
        }
        if (!buffer.hasRemaining()) {
            return -1;
        }

        len = Math.min(len, buffer.remaining());
        buffer.get(bytes, off, len);
        return len;
    }

    /**
     * Returns an estimate of the number of bytes that can be read from this input stream.
     *
     * @return the number of bytes remaining in the buffer
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int available() throws IOException {
        return buffer.remaining();
    }

    /**
     * Skips over and discards n bytes of data from this input stream.
     * This implementation is O(1) as it simply advances the buffer position.
     *
     * @param n the number of bytes to be skipped
     * @return the actual number of bytes skipped
     */
    @Override
    public long skip(long n) {
        if (n <= 0) {
            return 0;
        }
        int remaining = buffer.remaining();
        int toSkip = (int) Math.min(n, remaining);
        buffer.position(buffer.position() + toSkip);
        return toSkip;
    }
}
