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

package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Data transfer object for a row in {@code webhook_events}. Stores the serialized CloudEvent bytes
 * once; all delivery rows for this event reference the same row. {@code identityHash} is the
 * SHA-256 of the length-prefixed (source, eventId) pair used as a fixed-width deduplication key.
 * {@code payload} is raw bytes; retries resend the byte-identical content.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class WebhookEventDto {

    private String eventRowId;
    private String source;
    private String eventId;
    /** SHA-256 hex digest of length-prefixed (source, eventId) bytes; 64 lowercase hex chars. */
    private String identityHash;
    private String eventType;
    /** Serialized CloudEvent bytes; stored and resent verbatim on retry. */
    private byte[] payload;
    private long createdOn;
}
