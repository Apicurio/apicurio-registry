package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@Builder
@EqualsAndHashCode
@Getter
@Setter
@ToString
@RegisterForReflection
public class DownloadContextDto {

    private DownloadContextType type;
    private long expires;

    private Long globalId;
    private Long contentId;
    private String contentHash;

    /**
     * Constructor.
     */
    public DownloadContextDto() {
    }

}
