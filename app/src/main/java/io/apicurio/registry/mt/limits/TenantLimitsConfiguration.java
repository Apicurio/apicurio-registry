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

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * NOTE: Follow the naming conventions from {@link io.apicurio.registry.rest.v2.beans.Limits}
 *
 * @author Fabian Martinez
 */
@Getter
@Setter
@ToString
public class TenantLimitsConfiguration {

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
