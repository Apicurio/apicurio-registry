/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.sql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes the identity hash for a CloudEvent, used to deduplicate events in the
 * {@code webhook_events} table without requiring a unique index over two unbounded TEXT columns.
 * <p>
 * A CloudEvent is identified by the pair ({@code source}, {@code id}). Both are unbounded strings,
 * so a unique index across them is not portable. Instead this class produces a fixed-width SHA-256
 * digest that fits in a {@code VARCHAR(64)} unique index:
 * <pre>
 *   identityHash = SHA-256( len4(source) || source_utf8 || len4(id) || id_utf8 )
 * </pre>
 * Each value is length-prefixed with its UTF-8 byte length as a 4-byte big-endian integer. The
 * prefix prevents the pair {@code ("ab","c")} from colliding with {@code ("a","bc")} by making the
 * boundary between the two values unambiguous.
 * <p>
 * The digest is a lookup key, not the truth. On a match, the caller must compare {@code source}
 * and {@code eventId} verbatim and treat a collision as a conflict rather than a duplicate.
 */
public final class WebhookEventIdentity {

    private WebhookEventIdentity() {
    }

    /**
     * Computes the identity hash for the given CloudEvent (source, id) pair.
     *
     * @param source  the CloudEvent {@code source} attribute
     * @param eventId the CloudEvent {@code id} attribute
     * @return 64-character lowercase hexadecimal SHA-256 digest
     */
    public static String computeHash(String source, String eventId) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        byte[] idBytes = eventId.getBytes(StandardCharsets.UTF_8);

        byte[] input = new byte[4 + sourceBytes.length + 4 + idBytes.length];
        int pos = 0;
        pos = writeLen4(input, pos, sourceBytes.length);
        System.arraycopy(sourceBytes, 0, input, pos, sourceBytes.length);
        pos += sourceBytes.length;
        pos = writeLen4(input, pos, idBytes.length);
        System.arraycopy(idBytes, 0, input, pos, idBytes.length);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static int writeLen4(byte[] buf, int pos, int len) {
        buf[pos] = (byte) ((len >>> 24) & 0xFF);
        buf[pos + 1] = (byte) ((len >>> 16) & 0xFF);
        buf[pos + 2] = (byte) ((len >>> 8) & 0xFF);
        buf[pos + 3] = (byte) (len & 0xFF);
        return pos + 4;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append("0123456789abcdef".charAt(v >>> 4));
            sb.append("0123456789abcdef".charAt(v & 0x0F));
        }
        return sb.toString();
    }
}
