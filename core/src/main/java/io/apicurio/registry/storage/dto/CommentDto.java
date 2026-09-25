package io.apicurio.registry.storage.dto;

import lombok.*;

/**
 * Data transfer object representing a comment attached to an artifact version. Comments allow users to
 * annotate specific versions with free-text notes.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class CommentDto {

    private String commentId;
    private String value;
    private String owner;
    private long createdOn;
}
