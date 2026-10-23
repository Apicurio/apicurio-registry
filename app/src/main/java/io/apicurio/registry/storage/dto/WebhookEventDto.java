package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
