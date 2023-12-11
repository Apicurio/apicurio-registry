package io.apicurio.registry.limits;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * NOTE: Follow the naming conventions from {@link io.apicurio.registry.rest.v3.beans.Limits}
 *
 */
@Getter
@Setter
@ToString
public class RegistryLimitsConfiguration {

    private Long maxTotalSchemasCount;
    private Long maxSchemaSizeBytes;

    private Long maxArtifactsCount;
    private Long maxVersionsPerArtifactCount;

    private Long maxArtifactPropertiesCount;
    private Long maxPropertyKeySizeBytes;
    private Long maxPropertyValueSizeBytes;

    private Long maxArtifactLabelsCount;
    private Long maxLabelSizeBytes;

    private Long maxArtifactNameLengthChars;
    private Long maxArtifactDescriptionLengthChars;

    private Long maxRequestsPerSecondCount;
}
