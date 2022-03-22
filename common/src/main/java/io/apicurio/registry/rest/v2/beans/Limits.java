
package io.apicurio.registry.rest.v2.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.annotation.processing.Generated;


/**
 * Root Type for Limits
 * <p>
 * List of limitations on used resources, that are applied on the current instance of Registry.
 * Keys represent the resource type and are suffixed by the corresponding unit.
 * Values are integers. Only non-negative values are allowed, with the exception of -1, which means that the limit is not applied.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "maxTotalSchemasCount",
    "maxArtifactsCount",
    "maxVersionsPerArtifactCount",
    "maxArtifactPropertiesCount",
    "maxPropertyKeySizeBytes",
    "maxPropertyValueSizeBytes",
    "maxArtifactLabelsCount",
    "maxLabelSizeBytes",
    "maxArtifactNameLengthChars",
    "maxArtifactDescriptionLengthChars",
    "maxRequestsPerSecondCount"
})
@Generated("jsonschema2pojo")
@io.quarkus.runtime.annotations.RegisterForReflection
@lombok.ToString
public class Limits {

    @JsonProperty("maxTotalSchemasCount")
    private Long maxTotalSchemasCount;
    @JsonProperty("maxArtifactsCount")
    private Long maxArtifactsCount;
    @JsonProperty("maxVersionsPerArtifactCount")
    private Long maxVersionsPerArtifactCount;
    @JsonProperty("maxArtifactPropertiesCount")
    private Long maxArtifactPropertiesCount;
    @JsonProperty("maxPropertyKeySizeBytes")
    private Long maxPropertyKeySizeBytes;
    @JsonProperty("maxPropertyValueSizeBytes")
    private Long maxPropertyValueSizeBytes;
    @JsonProperty("maxArtifactLabelsCount")
    private Long maxArtifactLabelsCount;
    @JsonProperty("maxLabelSizeBytes")
    private Long maxLabelSizeBytes;
    @JsonProperty("maxArtifactNameLengthChars")
    private Long maxArtifactNameLengthChars;
    @JsonProperty("maxArtifactDescriptionLengthChars")
    private Long maxArtifactDescriptionLengthChars;
    @JsonProperty("maxRequestsPerSecondCount")
    private Long maxRequestsPerSecondCount;

    @JsonProperty("maxTotalSchemasCount")
    public Long getMaxTotalSchemasCount() {
        return maxTotalSchemasCount;
    }

    @JsonProperty("maxTotalSchemasCount")
    public void setMaxTotalSchemasCount(Long maxTotalSchemasCount) {
        this.maxTotalSchemasCount = maxTotalSchemasCount;
    }

    @JsonProperty("maxArtifactsCount")
    public Long getMaxArtifactsCount() {
        return maxArtifactsCount;
    }

    @JsonProperty("maxArtifactsCount")
    public void setMaxArtifactsCount(Long maxArtifactsCount) {
        this.maxArtifactsCount = maxArtifactsCount;
    }

    @JsonProperty("maxVersionsPerArtifactCount")
    public Long getMaxVersionsPerArtifactCount() {
        return maxVersionsPerArtifactCount;
    }

    @JsonProperty("maxVersionsPerArtifactCount")
    public void setMaxVersionsPerArtifactCount(Long maxVersionsPerArtifactCount) {
        this.maxVersionsPerArtifactCount = maxVersionsPerArtifactCount;
    }

    @JsonProperty("maxArtifactPropertiesCount")
    public Long getMaxArtifactPropertiesCount() {
        return maxArtifactPropertiesCount;
    }

    @JsonProperty("maxArtifactPropertiesCount")
    public void setMaxArtifactPropertiesCount(Long maxArtifactPropertiesCount) {
        this.maxArtifactPropertiesCount = maxArtifactPropertiesCount;
    }

    @JsonProperty("maxPropertyKeySizeBytes")
    public Long getMaxPropertyKeySizeBytes() {
        return maxPropertyKeySizeBytes;
    }

    @JsonProperty("maxPropertyKeySizeBytes")
    public void setMaxPropertyKeySizeBytes(Long maxPropertyKeySizeBytes) {
        this.maxPropertyKeySizeBytes = maxPropertyKeySizeBytes;
    }

    @JsonProperty("maxPropertyValueSizeBytes")
    public Long getMaxPropertyValueSizeBytes() {
        return maxPropertyValueSizeBytes;
    }

    @JsonProperty("maxPropertyValueSizeBytes")
    public void setMaxPropertyValueSizeBytes(Long maxPropertyValueSizeBytes) {
        this.maxPropertyValueSizeBytes = maxPropertyValueSizeBytes;
    }

    @JsonProperty("maxArtifactLabelsCount")
    public Long getMaxArtifactLabelsCount() {
        return maxArtifactLabelsCount;
    }

    @JsonProperty("maxArtifactLabelsCount")
    public void setMaxArtifactLabelsCount(Long maxArtifactLabelsCount) {
        this.maxArtifactLabelsCount = maxArtifactLabelsCount;
    }

    @JsonProperty("maxLabelSizeBytes")
    public Long getMaxLabelSizeBytes() {
        return maxLabelSizeBytes;
    }

    @JsonProperty("maxLabelSizeBytes")
    public void setMaxLabelSizeBytes(Long maxLabelSizeBytes) {
        this.maxLabelSizeBytes = maxLabelSizeBytes;
    }

    @JsonProperty("maxArtifactNameLengthChars")
    public Long getMaxArtifactNameLengthChars() {
        return maxArtifactNameLengthChars;
    }

    @JsonProperty("maxArtifactNameLengthChars")
    public void setMaxArtifactNameLengthChars(Long maxArtifactNameLengthChars) {
        this.maxArtifactNameLengthChars = maxArtifactNameLengthChars;
    }

    @JsonProperty("maxArtifactDescriptionLengthChars")
    public Long getMaxArtifactDescriptionLengthChars() {
        return maxArtifactDescriptionLengthChars;
    }

    @JsonProperty("maxArtifactDescriptionLengthChars")
    public void setMaxArtifactDescriptionLengthChars(Long maxArtifactDescriptionLengthChars) {
        this.maxArtifactDescriptionLengthChars = maxArtifactDescriptionLengthChars;
    }

    @JsonProperty("maxRequestsPerSecondCount")
    public Long getMaxRequestsPerSecondCount() {
        return maxRequestsPerSecondCount;
    }

    @JsonProperty("maxRequestsPerSecondCount")
    public void setMaxRequestsPerSecondCount(Long maxRequestsPerSecondCount) {
        this.maxRequestsPerSecondCount = maxRequestsPerSecondCount;
    }

}
