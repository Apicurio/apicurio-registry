package io.apicurio.registry.storage.dto;

import lombok.*;

import java.io.Serializable;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class GroupMetaDataDto implements Serializable {

    private static final long serialVersionUID = -9015518049780762742L;

    private String groupId;
    private String description;
    private String artifactsType;
    private String createdBy;
    private long createdOn;
    private String modifiedBy;
    private long modifiedOn;
    private Map<String, String> properties;
}
