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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebhookEventIdentityTest {

    @Test
    void sameInputProducesSameHash() {
        String hash1 = WebhookEventIdentity.computeHash("https://example.com/source", "evt-001");
        String hash2 = WebhookEventIdentity.computeHash("https://example.com/source", "evt-001");
        assertEquals(hash1, hash2);
    }

    @Test
    void prefixAmbiguityIsResolved() {
        // Without length-prefixing, ("ab","c") and ("a","bc") would hash to the same bytes.
        String hash1 = WebhookEventIdentity.computeHash("ab", "c");
        String hash2 = WebhookEventIdentity.computeHash("a", "bc");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void differentSourcesProduceDifferentHashes() {
        String hash1 = WebhookEventIdentity.computeHash("https://source-a.com", "evt-001");
        String hash2 = WebhookEventIdentity.computeHash("https://source-b.com", "evt-001");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void differentEventIdsProduceDifferentHashes() {
        String hash1 = WebhookEventIdentity.computeHash("https://example.com/source", "evt-001");
        String hash2 = WebhookEventIdentity.computeHash("https://example.com/source", "evt-002");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashIs64LowercaseHexChars() {
        String hash = WebhookEventIdentity.computeHash("https://example.com/source", "evt-001");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"), "Hash must be lowercase hex: " + hash);
    }

    @Test
    void emptyStringsProduceValidHash() {
        String hash = WebhookEventIdentity.computeHash("", "");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void unicodeSourceAndIdAreHandled() {
        // Multi-byte UTF-8: source and id both contain characters outside ASCII.
        String hash1 = WebhookEventIdentity.computeHash("https://例.com", "イベント-001");
        String hash2 = WebhookEventIdentity.computeHash("https://例.com", "イベント-002");
        assertNotEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    void goldenDigestVector() {
        // Stable reference: if the algorithm changes, this will catch it.
        // Value computed independently via Python:
        //   struct.pack('>I',19) + b'https://example.com' + struct.pack('>I',7) + b'evt-001'
        //   → SHA-256 → hex
        String hash = WebhookEventIdentity.computeHash("https://example.com", "evt-001");
        assertEquals("adc5482fe25bd350de203d6760f1973b1d28ea1b533dd06cebea5ad49611af6b", hash);
    }

    @Test
    void lengthPrefixEncodesUTF8ByteLengthNotCharLength() {
        // 'ñ' is 1 char but 2 UTF-8 bytes. The prefix must encode byte length (2), not char
        // length (1). If it encoded char length, ("ñ","x") and ("n","x") would produce the same
        // prefix value and could collide under certain inputs.
        String hashMultiByte = WebhookEventIdentity.computeHash("ñ", "x");
        String hashSingleByte = WebhookEventIdentity.computeHash("n", "x");
        assertNotEquals(hashMultiByte, hashSingleByte,
                "Byte-length prefix must distinguish same-char-length but different-byte-length sources");
        // Verify against independently computed values.
        assertEquals("086b1fe776b68a44efe37fb932a1972c6155df2b77a727168f86b378356ca715", hashMultiByte);
        assertEquals("8733f682871a4db10ae2f17f9f49577c2f16c001a89518d49eca05b0bc009442", hashSingleByte);
    }

    @Test
    void caseDifferencesProduceDifferentHashes() {
        // No case normalization must happen before hashing.
        String hashUpper = WebhookEventIdentity.computeHash("ABC", "x");
        String hashLower = WebhookEventIdentity.computeHash("abc", "x");
        assertNotEquals(hashUpper, hashLower, "Upper and lower case must produce different hashes");
        assertEquals("23d10cbe1827874d37802fa7478234b0cb710b52879722e071d17ab807b390d2", hashUpper);
        assertEquals("aec75384e630a4ba5a158595a0ce1397873322eca8dcc5c39491728ff492b916", hashLower);
    }

    @Test
    void trailingSpaceProducesDifferentHash() {
        // No trimming must happen before hashing.
        String hashNoSpace = WebhookEventIdentity.computeHash("abc", "x");
        String hashWithSpace = WebhookEventIdentity.computeHash("abc ", "x");
        assertNotEquals(hashNoSpace, hashWithSpace, "Trailing space must produce a different hash");
        assertEquals("55cbab6f2e6301ce4b2af5d60e624999f54dcbdeecaf7ffbd88c828bef85d3de", hashWithSpace);
    }

    @Test
    void nullSourceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookEventIdentity.computeHash(null, "evt-001"),
                "null source must throw IllegalArgumentException");
    }

    @Test
    void nullEventIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookEventIdentity.computeHash("https://example.com", null),
                "null eventId must throw IllegalArgumentException");
    }
}
