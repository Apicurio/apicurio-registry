package io.apicurio.registry.storage.impl.polling.model.v0;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@NoArgsConstructor
@Setter
@Getter
@EqualsAndHashCode
@ToString
public class Version {

    private String version;

    private String state;

    private String name;

    private String description;

    private Map<String, String> labels;

    private String content;

    private String contentMetadata;

    private Long globalId;

    private String owner;

    private String createdOn;

    private String modifiedOn;
}
