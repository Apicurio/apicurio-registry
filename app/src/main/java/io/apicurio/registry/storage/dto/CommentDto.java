package io.apicurio.registry.storage.dto;

import lombok.*;

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
    private String createdBy;
    private long createdOn;
}
