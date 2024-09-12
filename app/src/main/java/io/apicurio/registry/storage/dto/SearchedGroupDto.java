package io.apicurio.registry.storage.dto;

import lombok.*;

import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SearchedGroupDto {

    private String id;
    private String description;
    private Date createdOn;
    private String owner;
    private Date modifiedOn;
    private String modifiedBy;
}
