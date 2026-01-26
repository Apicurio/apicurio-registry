package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Data transfer object representing a content hash entry.
 * Each content row can have multiple hashes of different types.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ContentHashDto {

    /**
     * The content ID this hash belongs to.
     */
    private long contentId;

    /**
     * The type of hash.
     */
    private ContentHashType hashType;

    /**
     * The hash value.
     */
    private String hashValue;

    /**
     * When this hash was created (milliseconds since epoch).
     */
    private long createdOn;
}
