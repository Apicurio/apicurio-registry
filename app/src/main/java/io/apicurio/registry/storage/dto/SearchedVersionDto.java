package io.apicurio.registry.storage.dto;

import io.apicurio.registry.types.VersionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SearchedVersionDto {

    private String groupId;
    private String artifactId;
    private String version;
    private String name;
    private String description;
    private Date createdOn;
    private String owner;
    private String artifactType;
    private VersionState state;
    private long globalId;
    private long contentId;
    private int versionOrder;
}
