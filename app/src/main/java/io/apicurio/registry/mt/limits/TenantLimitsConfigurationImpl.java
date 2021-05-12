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
public class TenantLimitsConfigurationImpl {

    private long maxTotalSchemas;
    private long maxArtifacts;
    private long maxVersionsPerArtifact;

    //TODO content size

    private long maxArtifactProperties;
    private long maxPropertyKeyBytesSize;
    private long maxPropertyValueBytesSize;

    private long maxArtifactLabels;
    private long maxLabelBytesSize;

    private long maxNameLength;
    private long maxDescriptionLength;

    /**
     * @return the maxTotalSchemas
     */
    public long getMaxTotalSchemas() {
        return maxTotalSchemas;
    }

    /**
     * @param maxTotalSchemas the maxTotalSchemas to set
     */
    public void setMaxTotalSchemas(long maxTotalSchemas) {
        this.maxTotalSchemas = maxTotalSchemas;
    }

    /**
     * @return the maxArtifacts
     */
    public long getMaxArtifacts() {
        return maxArtifacts;
    }

    /**
     * @param maxArtifacts the maxArtifacts to set
     */
    public void setMaxArtifacts(long maxArtifacts) {
        this.maxArtifacts = maxArtifacts;
    }

    /**
     * @return the maxVersionsPerArtifact
     */
    public long getMaxVersionsPerArtifact() {
        return maxVersionsPerArtifact;
    }

    /**
     * @param maxVersionsPerArtifact the maxVersionsPerArtifact to set
     */
    public void setMaxVersionsPerArtifact(long maxVersionsPerArtifact) {
        this.maxVersionsPerArtifact = maxVersionsPerArtifact;
    }

    /**
     * @return the maxArtifactProperties
     */
    public long getMaxArtifactProperties() {
        return maxArtifactProperties;
    }

    /**
     * @param maxArtifactProperties the maxArtifactProperties to set
     */
    public void setMaxArtifactProperties(long maxArtifactProperties) {
        this.maxArtifactProperties = maxArtifactProperties;
    }

    /**
     * @return the maxPropertyKeyBytesSize
     */
    public long getMaxPropertyKeyBytesSize() {
        return maxPropertyKeyBytesSize;
    }

    /**
     * @param maxPropertyKeyBytesSize the maxPropertyKeyBytesSize to set
     */
    public void setMaxPropertyKeyBytesSize(long maxPropertyKeyBytesSize) {
        this.maxPropertyKeyBytesSize = maxPropertyKeyBytesSize;
    }

    /**
     * @return the maxPropertyValueBytesSize
     */
    public long getMaxPropertyValueBytesSize() {
        return maxPropertyValueBytesSize;
    }

    /**
     * @param maxPropertyValueBytesSize the maxPropertyValueBytesSize to set
     */
    public void setMaxPropertyValueBytesSize(long maxPropertyValueBytesSize) {
        this.maxPropertyValueBytesSize = maxPropertyValueBytesSize;
    }

    /**
     * @return the maxArtifactLabels
     */
    public long getMaxArtifactLabels() {
        return maxArtifactLabels;
    }

    /**
     * @param maxArtifactLabels the maxArtifactLabels to set
     */
    public void setMaxArtifactLabels(long maxArtifactLabels) {
        this.maxArtifactLabels = maxArtifactLabels;
    }

    /**
     * @return the maxLabelBytesSize
     */
    public long getMaxLabelBytesSize() {
        return maxLabelBytesSize;
    }

    /**
     * @param maxLabelBytesSize the maxLabelBytesSize to set
     */
    public void setMaxLabelBytesSize(long maxLabelBytesSize) {
        this.maxLabelBytesSize = maxLabelBytesSize;
    }

    /**
     * @return the maxNameLength
     */
    public long getMaxNameLength() {
        return maxNameLength;
    }

    /**
     * @param maxNameLength the maxNameLength to set
     */
    public void setMaxNameLength(long maxNameLength) {
        this.maxNameLength = maxNameLength;
    }

    /**
     * @return the maxDescriptionLength
     */
    public long getMaxDescriptionLength() {
        return maxDescriptionLength;
    }

    /**
     * @param maxDescriptionLength the maxDescriptionLength to set
     */
    public void setMaxDescriptionLength(long maxDescriptionLength) {
        this.maxDescriptionLength = maxDescriptionLength;
    }

}
