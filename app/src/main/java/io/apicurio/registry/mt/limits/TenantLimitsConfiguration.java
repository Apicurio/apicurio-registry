/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.mt.limits;

/**
 * @author Fabian Martinez
 */
public class TenantLimitsConfiguration {

    private Long maxTotalSchemas;
    private Long maxArtifacts;
    private Long maxVersionsPerArtifact;

    //TODO content size

    private Long maxArtifactProperties;
    private Long maxPropertyKeyBytesSize;
    private Long maxPropertyValueBytesSize;

    private Long maxArtifactLabels;
    private Long maxLabelBytesSize;

    private Long maxNameLength;
    private Long maxDescriptionLength;
    /**
     * @return the maxTotalSchemas
     */
    public Long getMaxTotalSchemas() {
        return maxTotalSchemas;
    }
    /**
     * @param maxTotalSchemas the maxTotalSchemas to set
     */
    public void setMaxTotalSchemas(Long maxTotalSchemas) {
        this.maxTotalSchemas = maxTotalSchemas;
    }
    /**
     * @return the maxArtifacts
     */
    public Long getMaxArtifacts() {
        return maxArtifacts;
    }
    /**
     * @param maxArtifacts the maxArtifacts to set
     */
    public void setMaxArtifacts(Long maxArtifacts) {
        this.maxArtifacts = maxArtifacts;
    }
    /**
     * @return the maxVersionsPerArtifact
     */
    public Long getMaxVersionsPerArtifact() {
        return maxVersionsPerArtifact;
    }
    /**
     * @param maxVersionsPerArtifact the maxVersionsPerArtifact to set
     */
    public void setMaxVersionsPerArtifact(Long maxVersionsPerArtifact) {
        this.maxVersionsPerArtifact = maxVersionsPerArtifact;
    }
    /**
     * @return the maxArtifactProperties
     */
    public Long getMaxArtifactProperties() {
        return maxArtifactProperties;
    }
    /**
     * @param maxArtifactProperties the maxArtifactProperties to set
     */
    public void setMaxArtifactProperties(Long maxArtifactProperties) {
        this.maxArtifactProperties = maxArtifactProperties;
    }
    /**
     * @return the maxPropertyKeyBytesSize
     */
    public Long getMaxPropertyKeyBytesSize() {
        return maxPropertyKeyBytesSize;
    }
    /**
     * @param maxPropertyKeyBytesSize the maxPropertyKeyBytesSize to set
     */
    public void setMaxPropertyKeyBytesSize(Long maxPropertyKeyBytesSize) {
        this.maxPropertyKeyBytesSize = maxPropertyKeyBytesSize;
    }
    /**
     * @return the maxPropertyValueBytesSize
     */
    public Long getMaxPropertyValueBytesSize() {
        return maxPropertyValueBytesSize;
    }
    /**
     * @param maxPropertyValueBytesSize the maxPropertyValueBytesSize to set
     */
    public void setMaxPropertyValueBytesSize(Long maxPropertyValueBytesSize) {
        this.maxPropertyValueBytesSize = maxPropertyValueBytesSize;
    }
    /**
     * @return the maxArtifactLabels
     */
    public Long getMaxArtifactLabels() {
        return maxArtifactLabels;
    }
    /**
     * @param maxArtifactLabels the maxArtifactLabels to set
     */
    public void setMaxArtifactLabels(Long maxArtifactLabels) {
        this.maxArtifactLabels = maxArtifactLabels;
    }
    /**
     * @return the maxLabelBytesSize
     */
    public Long getMaxLabelBytesSize() {
        return maxLabelBytesSize;
    }
    /**
     * @param maxLabelBytesSize the maxLabelBytesSize to set
     */
    public void setMaxLabelBytesSize(Long maxLabelBytesSize) {
        this.maxLabelBytesSize = maxLabelBytesSize;
    }
    /**
     * @return the maxNameLength
     */
    public Long getMaxNameLength() {
        return maxNameLength;
    }
    /**
     * @param maxNameLength the maxNameLength to set
     */
    public void setMaxNameLength(Long maxNameLength) {
        this.maxNameLength = maxNameLength;
    }
    /**
     * @return the maxDescriptionLength
     */
    public Long getMaxDescriptionLength() {
        return maxDescriptionLength;
    }
    /**
     * @param maxDescriptionLength the maxDescriptionLength to set
     */
    public void setMaxDescriptionLength(Long maxDescriptionLength) {
        this.maxDescriptionLength = maxDescriptionLength;
    }

}
