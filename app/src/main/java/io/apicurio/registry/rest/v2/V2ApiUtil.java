/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

package io.apicurio.registry.rest.v2;

import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.rest.v2.beans.SearchedVersion;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.CustomRule;
import io.apicurio.registry.rest.v2.beans.CustomRuleBinding;
import io.apicurio.registry.rest.v2.beans.CustomRuleInfo;
import io.apicurio.registry.rest.v2.beans.CustomRuleUpdate;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.rest.v2.beans.WebhookCustomRuleConfig;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.CustomRuleBindingDto;
import io.apicurio.registry.storage.dto.CustomRuleDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableCustomRuleDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.CustomRuleType;
import io.apicurio.registry.types.RegistryException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author eric.wittmann@gmail.com
 */
public final class V2ApiUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Creates a jax-rs meta-data entity from the id, type, and artifactStore meta-data.
     *
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final ArtifactMetaData dtoToMetaData(String groupId, String artifactId,
            ArtifactType artifactType, ArtifactMetaDataDto dto) {
        ArtifactMetaData metaData = new ArtifactMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(new Date(dto.getCreatedOn()));
        metaData.setDescription(dto.getDescription());
        if (groupId != null) {
            metaData.setGroupId(groupId);
        } else {
            metaData.setGroupId(dto.getGroupId());
        }
        if (artifactId != null) {
            metaData.setId(artifactId);
        } else {
            metaData.setId(dto.getId());
        }
        metaData.setModifiedBy(dto.getModifiedBy());
        metaData.setModifiedOn(new Date(dto.getModifiedOn()));
        metaData.setName(dto.getName());
        if (artifactType != null) {
            metaData.setType(artifactType);
        } else {
            metaData.setType(dto.getType());
        }
        metaData.setVersion(dto.getVersion());
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setContentId(dto.getContentId());
        metaData.setState(dto.getState());
        metaData.setLabels(dto.getLabels());
        metaData.setProperties(dto.getProperties());
        return metaData;
    }

    /**
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final ArtifactMetaData dtoToMetaData(String groupId, String artifactId, ArtifactType artifactType,
            ArtifactVersionMetaDataDto dto) {
        ArtifactMetaData metaData = new ArtifactMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(new Date(dto.getCreatedOn()));
        metaData.setDescription(dto.getDescription());
        metaData.setGroupId(groupId);
        metaData.setId(artifactId);
        metaData.setModifiedBy(dto.getCreatedBy());
        metaData.setModifiedOn(new Date(dto.getCreatedOn()));
        metaData.setName(dto.getName());
        if (artifactType != null) {
            metaData.setType(artifactType);
        } else {
            metaData.setType(dto.getType());
        }
        metaData.setVersion(dto.getVersion());
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setContentId(dto.getContentId());
        metaData.setState(dto.getState());
        metaData.setLabels(dto.getLabels());
        metaData.setProperties(dto.getProperties());
        return metaData;
    }


    /**
     * Creates a jax-rs version meta-data entity from the id, type, and artifactStore meta-data.
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final VersionMetaData dtoToVersionMetaData(String groupId, String artifactId,
            ArtifactType artifactType, ArtifactMetaDataDto dto) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setGroupId(groupId);
        metaData.setId(artifactId);
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(new Date(dto.getCreatedOn()));
        metaData.setDescription(dto.getDescription());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(dto.getVersion());
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setContentId(dto.getContentId());
        metaData.setState(dto.getState());
        metaData.setLabels(dto.getLabels());
        metaData.setProperties(dto.getProperties());
        return metaData;
    }

    /**
     * Creates a jax-rs version meta-data entity from the id, type, and artifactStore meta-data.
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param amd
     */
    public static final VersionMetaData dtoToVersionMetaData(String groupId, String artifactId,
            ArtifactType artifactType, ArtifactMetaData amd) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setGroupId(groupId);
        metaData.setId(artifactId);
        metaData.setCreatedBy(amd.getCreatedBy());
        metaData.setCreatedOn(amd.getCreatedOn());
        metaData.setDescription(amd.getDescription());
        metaData.setName(amd.getName());
        metaData.setType(artifactType);
        metaData.setVersion(amd.getVersion());
        metaData.setGlobalId(amd.getGlobalId());
        metaData.setState(amd.getState());
        metaData.setLabels(amd.getLabels());
        metaData.setProperties(amd.getProperties());
        return metaData;
    }

    /**
     * Creates a jax-rs version meta-data entity from the id, type, and artifactStore meta-data.
     *
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final VersionMetaData dtoToVersionMetaData(String groupId, String artifactId, ArtifactType artifactType,
                                                             ArtifactVersionMetaDataDto dto) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setGroupId(groupId);
        metaData.setId(artifactId);
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(new Date(dto.getCreatedOn()));
        metaData.setDescription(dto.getDescription());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(dto.getVersion());
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setContentId(dto.getContentId());
        metaData.setState(dto.getState());
        metaData.setLabels(dto.getLabels());
        metaData.setProperties(dto.getProperties());
        return metaData;
    }

    /**
     * Sets values from the EditableArtifactMetaDataDto into the ArtifactMetaDataDto.
     *
     * @param amdd
     * @param editableArtifactMetaData
     * @return the updated ArtifactMetaDataDto object
     */
    public static final ArtifactMetaDataDto setEditableMetaDataInArtifact(ArtifactMetaDataDto amdd, EditableArtifactMetaDataDto editableArtifactMetaData) {
        if (editableArtifactMetaData.getName() != null) {
            amdd.setName(editableArtifactMetaData.getName());
        }
        if (editableArtifactMetaData.getDescription() != null) {
            amdd.setDescription(editableArtifactMetaData.getDescription());
        }
        if (editableArtifactMetaData.getLabels() != null && !editableArtifactMetaData.getLabels().isEmpty()) {
            amdd.setLabels(editableArtifactMetaData.getLabels());
        }
        if (editableArtifactMetaData.getProperties() != null) {
            amdd.setProperties(editableArtifactMetaData.getProperties());
        }
        return amdd;
    }

    public static Comparator<ArtifactMetaDataDto> comparator(SortOrder sortOrder) {
        return (id1, id2) -> compare(sortOrder, id1, id2);
    }

    public static int compare(SortOrder sortOrder, ArtifactMetaDataDto metaDataDto1, ArtifactMetaDataDto metaDataDto2) {
        String name1 = metaDataDto1.getName();
        if (name1 == null) {
            name1 = metaDataDto1.getId();
        }
        String name2 = metaDataDto2.getName();
        if (name2 == null) {
            name2 = metaDataDto2.getId();
        }
        return sortOrder == SortOrder.desc ? name2.compareToIgnoreCase(name1) : name1.compareToIgnoreCase(name2);
    }

    public static ArtifactSearchResults dtoToSearchResults(ArtifactSearchResultsDto dto) {
        ArtifactSearchResults results = new ArtifactSearchResults();
        results.setCount((int) dto.getCount());
        results.setArtifacts(new ArrayList<>(dto.getArtifacts().size()));
        dto.getArtifacts().forEach(artifact -> {
            SearchedArtifact sa = new SearchedArtifact();
            sa.setCreatedBy(artifact.getCreatedBy());
            sa.setCreatedOn(artifact.getCreatedOn());
            sa.setDescription(artifact.getDescription());
            sa.setId(artifact.getId());
            sa.setGroupId(artifact.getGroupId());
            sa.setLabels(artifact.getLabels());
            sa.setModifiedBy(artifact.getModifiedBy());
            sa.setModifiedOn(artifact.getModifiedOn());
            sa.setName(artifact.getName());
            sa.setState(artifact.getState());
            sa.setType(artifact.getType());
            results.getArtifacts().add(sa);
        });
        return results;
    }

    public static VersionSearchResults dtoToSearchResults(VersionSearchResultsDto dto) {
        VersionSearchResults results = new VersionSearchResults();
        results.setCount((int) dto.getCount());
        results.setVersions(new ArrayList<>(dto.getVersions().size()));
        dto.getVersions().forEach(version -> {
            SearchedVersion sv = new SearchedVersion();
            sv.setCreatedBy(version.getCreatedBy());
            sv.setCreatedOn(version.getCreatedOn());
            sv.setDescription(version.getDescription());
            sv.setGlobalId(version.getGlobalId());
            sv.setContentId(version.getContentId());
            sv.setLabels(version.getLabels());
            sv.setName(version.getName());
            sv.setState(version.getState());
            sv.setType(version.getType());
            sv.setProperties(version.getProperties());
            sv.setVersion(version.getVersion());
            results.getVersions().add(sv);
        });
        return results;
    }

    public static CustomRule dtoToCustomRule(CustomRuleDto crd) {
        CustomRule cr = new CustomRule();
        cr.setCustomRuleType(crd.getCustomRuleType());
        cr.setSupportedArtifactType(crd.getSupportedArtifactType());
        cr.setId(crd.getId());
        cr.setDescription(crd.getDescription());

        try {
            switch (crd.getCustomRuleType()) {
                case webhook:
                    cr.setWebhookConfig(mapper.readValue(crd.getConfig(), WebhookCustomRuleConfig.class));
                    break;
                default:
                    throw new RegistryException("Custom rule type not implemented");
            }
        } catch (IOException e) {
            throw new RegistryException(e);
        }

        return cr;
    }

    public static EditableCustomRuleDto editableCustomRuleToDto(CustomRuleType type, CustomRuleUpdate ecr) {
        EditableCustomRuleDto dto = new EditableCustomRuleDto();
        dto.setDescription(ecr.getDescription());
        Object configObj;
        switch (type) {
            case webhook:
                configObj = requireParameter("custom rule config", ecr.getWebhookConfig());
                break;
            default:
                throw new RegistryException("Custom rule type not implemented");
        }

        String config;
        try {
            config = mapper.writeValueAsString(configObj);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        dto.setConfig(config);
        return dto;
    }

    public static CustomRuleDto customRuleToDto(CustomRule crd) {
        CustomRuleDto cr = new CustomRuleDto();
        cr.setCustomRuleType(crd.getCustomRuleType());
        cr.setSupportedArtifactType(crd.getSupportedArtifactType());
        cr.setId(crd.getId());
        cr.setDescription(crd.getDescription());

        Object configObj;
        switch (crd.getCustomRuleType()) {
            case webhook:
                configObj = requireParameter("custom rule config", crd.getWebhookConfig());
                break;
            default:
                throw new RegistryException("Custom rule type not implemented");
        }

        String config;
        try {
            config = mapper.writeValueAsString(configObj);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cr.setConfig(config);
        return cr;
    }

    public static CustomRuleInfo customRuleDtoToCustomRuleInfo(CustomRuleDto crd) {
        CustomRuleInfo cr = new CustomRuleInfo();
        cr.setCustomRuleType(crd.getCustomRuleType());
        cr.setSupportedArtifactType(crd.getSupportedArtifactType());
        cr.setId(crd.getId());
        cr.setDescription(crd.getDescription());
        return cr;
    }

    public static CustomRuleBinding dtoToCustomRuleBinding(CustomRuleBindingDto dto) {
        CustomRuleBinding crb = new CustomRuleBinding();
        crb.setCustomRuleId(dto.getCustomRuleId());
        return crb;
    }

    public static final <T> T requireParameter(String parameterName, T parameterValue) {
        if (parameterValue == null) {
            throw new MissingRequiredParameterException(parameterName);
        }
        return parameterValue;
    }
}
