package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class DownloadContextDto {

    private DownloadContextType type;
    private long expires;

    private Long globalId;
    private Long contentId;
    private String contentHash;

    // Fields for VERSION_EXPORT
    private String groupId;
    private String artifactId;
    private String version;
}
