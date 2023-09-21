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

import java.util.List;
import java.util.stream.Collectors;

import io.apicurio.registry.rest.v2.beans.ArtifactContent;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactOwner;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.ArtifactTypeInfo;
import io.apicurio.registry.rest.v2.beans.Comment;
import io.apicurio.registry.rest.v2.beans.ConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.GroupMetaData;
import io.apicurio.registry.rest.v2.beans.GroupSearchResults;
import io.apicurio.registry.rest.v2.beans.Limits;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.rest.v2.beans.SearchedGroup;
import io.apicurio.registry.rest.v2.beans.SearchedVersion;
import io.apicurio.registry.rest.v2.beans.SystemInfo;
import io.apicurio.registry.rest.v2.beans.UserInfo;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.rest.v3.beans.CreateGroupMetaData;
import io.apicurio.registry.rest.v3.beans.EditableMetaData;
import io.apicurio.registry.rest.v3.beans.IfExists;
import io.apicurio.registry.rest.v3.beans.NewComment;
import io.apicurio.registry.rest.v3.beans.Rule;
import io.apicurio.registry.rest.v3.beans.SortBy;
import io.apicurio.registry.rest.v3.beans.SortOrder;
import io.apicurio.registry.rest.v3.beans.UpdateConfigurationProperty;
import io.apicurio.registry.rest.v3.beans.UpdateRole;
import io.apicurio.registry.rest.v3.beans.UpdateState;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import jakarta.validation.constraints.NotNull;

/**
 * @author eric.wittmann@gmail.com
 */
public final class V2ApiUtil {

    private V2ApiUtil() {
    }

    public static String defaultGroupIdToNull(String groupId) {
        if ("default".equalsIgnoreCase(groupId)) {
            return null;
        }
        return groupId;
    }

    public static String nullGroupIdToDefault(String groupId) {
        return groupId != null ? groupId : "default";
    }
    
    public static ArtifactReferenceDto referenceToDto(ArtifactReference reference) {
        final ArtifactReferenceDto artifactReference = new ArtifactReferenceDto();
        artifactReference.setGroupId(reference.getGroupId());
        artifactReference.setName(reference.getName());
        artifactReference.setVersion(reference.getVersion());
        artifactReference.setArtifactId(reference.getArtifactId());
        return artifactReference;
    }

    public static ArtifactReference referenceDtoToReference(ArtifactReferenceDto reference) {
        final ArtifactReference artifactReference = new ArtifactReference();
        artifactReference.setGroupId(reference.getGroupId());
        artifactReference.setName(reference.getName());
        artifactReference.setVersion(reference.getVersion());
        artifactReference.setArtifactId(reference.getArtifactId());
        return artifactReference;
    }


    public static io.apicurio.registry.rest.v3.beans.ArtifactReference toV3(ArtifactReference v2) {
        if (v2 == null) {
            return null;
        }
        return io.apicurio.registry.rest.v3.beans.ArtifactReference.builder()
                .groupId(v2.getGroupId())
                .artifactId(v2.getArtifactId())
                .version(v2.getVersion())
                .name(v2.getName())
                .build();
    }
    
    public static List<io.apicurio.registry.rest.v3.beans.ArtifactReference> toV3_ArtifactReferenceList(List<ArtifactReference> v2) {
        if (v2 == null) {
            return null;
        }
        return v2.stream().map(reference -> {
            var v3ref = toV3(reference);
            return v3ref;
        }).collect(Collectors.toList());
    }
    
    public static ArtifactReference fromV3(io.apicurio.registry.rest.v3.beans.ArtifactReference v3) {
        if (v3 == null) {
            return null;
        }
        return ArtifactReference.builder()
                .groupId(v3.getGroupId())
                .artifactId(v3.getArtifactId())
                .version(v3.getVersion())
                .name(v3.getName())
                .build();
    }

    public static List<ArtifactReference> fromV3(List<io.apicurio.registry.rest.v3.beans.ArtifactReference> references) {
        if (references == null) {
            return null;
        }
        return references.stream().map(item -> {
            var ref = fromV3(item);
            return ref;
        }).collect(Collectors.toList());
    }

    public static UpdateState toV3(io.apicurio.registry.rest.v2.beans.UpdateState v2) {
        return UpdateState.builder().state(v2.getState()).build();
    }

    public static VersionMetaData fromV3(io.apicurio.registry.rest.v3.beans.VersionMetaData v3) {
        return VersionMetaData.builder()
                .contentId(v3.getContentId())
                .createdBy(v3.getCreatedBy())
                .createdOn(v3.getCreatedOn())
                .description(v3.getDescription())
                .globalId(v3.getGlobalId())
                .groupId(v3.getGroupId())
                .id(v3.getId())
                .labels(v3.getLabels())
                .name(v3.getName())
                .properties(v3.getProperties())
                .state(v3.getState())
                .type(v3.getType())
                .version(v3.getVersion())
                .build();
    }

    public static EditableMetaData toV3(io.apicurio.registry.rest.v2.beans.EditableMetaData v2) {
        return EditableMetaData.builder()
                .description(v2.getDescription())
                .labels(v2.getLabels())
                .name(v2.getName())
                .properties(v2.getProperties())
                .build();
    }

    public static Rule toV3(io.apicurio.registry.rest.v2.beans.Rule data) {
        return Rule.builder()
                .config(data.getConfig())
                .type(data.getType())
                .build();
    }

    public static ArtifactTypeInfo fromV3(io.apicurio.registry.rest.v3.beans.ArtifactTypeInfo v3) {
        return ArtifactTypeInfo.builder()
                .name(v3.getName())
                .build();
    }

    public static List<ArtifactTypeInfo> fromV3_ArtifactTypeInfoList(List<io.apicurio.registry.rest.v3.beans.ArtifactTypeInfo> v3) {
        if (v3 == null) {
            return null;
        }
        return v3.stream().map(item -> {
            var v2Info = fromV3(item);
            return v2Info;
        }).collect(Collectors.toList());
    }

    public static io.apicurio.registry.rest.v2.beans.Rule fromV3(Rule v3) {
        return io.apicurio.registry.rest.v2.beans.Rule.builder()
                .config(v3.getConfig())
                .type(v3.getType())
                .build();
    }

    public static RoleMapping fromV3(io.apicurio.registry.rest.v3.beans.RoleMapping v3) {
        return RoleMapping.builder()
                .principalId(v3.getPrincipalId())
                .principalName(v3.getPrincipalName())
                .role(v3.getRole())
                .build();
    }

    public static UpdateRole toV3(io.apicurio.registry.rest.v2.beans.UpdateRole v2) {
        return UpdateRole.builder()
                .role(v2.getRole())
                .build();
    }

    public static List<RoleMapping> fromV3_RoleMappingList(
            List<io.apicurio.registry.rest.v3.beans.RoleMapping> v3) {
        if (v3 == null) {
            return null;
        }
        return v3.stream().map(item -> {
            var v2 = fromV3(item);
            return v2;
        }).collect(Collectors.toList());
    }

    public static io.apicurio.registry.rest.v3.beans.RoleMapping toV3(RoleMapping v2) {
        return io.apicurio.registry.rest.v3.beans.RoleMapping.builder()
                .principalId(v2.getPrincipalId())
                .principalName(v2.getPrincipalName())
                .role(v2.getRole())
                .build();
    }

    public static ConfigurationProperty fromV3(io.apicurio.registry.rest.v3.beans.ConfigurationProperty v3) {
        return ConfigurationProperty.builder()
                .description(v3.getDescription())
                .label(v3.getLabel())
                .name(v3.getName())
                .type(v3.getType())
                .value(v3.getValue())
                .build();
    }

    public static List<ConfigurationProperty> fromV3_ConfigurationPropertyList(
            List<io.apicurio.registry.rest.v3.beans.ConfigurationProperty> v3) {
        if (v3 == null) {
            return null;
        }
        return v3.stream().map(item -> {
            var v2 = fromV3(item);
            return v2;
        }).collect(Collectors.toList());
    }

    public static UpdateConfigurationProperty toV3(
            io.apicurio.registry.rest.v2.beans.UpdateConfigurationProperty v3) {
        return UpdateConfigurationProperty.builder()
                .value(v3.getValue())
                .build();
    }

    public static ArtifactReference fromV3_ArtifactReferenceList(
            io.apicurio.registry.rest.v3.beans.ArtifactReference v3) {
        return ArtifactReference.builder()
                .artifactId(v3.getArtifactId())
                .groupId(v3.getGroupId())
                .name(v3.getName())
                .version(v3.getVersion())
                .build();
    }

    public static List<ArtifactReference> fromV3_ArtifactReferenceList(
            List<io.apicurio.registry.rest.v3.beans.ArtifactReference> v3) {
        if (v3 == null) {
            return null;
        }
        return v3.stream().map(item -> {
            var v2 = fromV3(item);
            return v2;
        }).collect(Collectors.toList());
    }

    public static SystemInfo fromV3(io.apicurio.registry.rest.v3.beans.SystemInfo v3) {
        return SystemInfo.builder()
                .builtOn(v3.getBuiltOn())
                .description(v3.getDescription())
                .name(v3.getName())
                .version(v3.getVersion())
                .build();
    }

    public static Limits fromV3(io.apicurio.registry.rest.v3.beans.Limits v3) {
        return Limits.builder()
                .maxArtifactDescriptionLengthChars(v3.getMaxArtifactDescriptionLengthChars())
                .maxArtifactLabelsCount(v3.getMaxArtifactLabelsCount())
                .maxArtifactNameLengthChars(v3.getMaxArtifactNameLengthChars())
                .maxArtifactPropertiesCount(v3.getMaxArtifactPropertiesCount())
                .maxArtifactsCount(v3.getMaxArtifactsCount())
                .maxLabelSizeBytes(v3.getMaxLabelSizeBytes())
                .maxPropertyKeySizeBytes(v3.getMaxPropertyKeySizeBytes())
                .maxPropertyValueSizeBytes(v3.getMaxPropertyValueSizeBytes())
                .maxRequestsPerSecondCount(v3.getMaxRequestsPerSecondCount())
                .maxSchemaSizeBytes(v3.getMaxSchemaSizeBytes())
                .maxTotalSchemasCount(v3.getMaxTotalSchemasCount())
                .maxVersionsPerArtifactCount(v3.getMaxVersionsPerArtifactCount())
                .build();
    }

    public static SortBy toV3(io.apicurio.registry.rest.v2.beans.SortBy v2) {
        if (v2 == null) {
            return SortBy.name;
        }
        switch (v2) {
            case createdOn:
                return SortBy.createdOn;
            case name:
                return SortBy.name;
        }
        return SortBy.name;
    }

    public static SortOrder toV3(io.apicurio.registry.rest.v2.beans.SortOrder v2) {
        if (v2 == null) {
            return SortOrder.desc;
        }
        switch (v2) {
            case asc:
                return SortOrder.asc;
            case desc:
                return SortOrder.desc;
        }
        return SortOrder.desc;
    }

    private static SearchedArtifact fromV3(
            io.apicurio.registry.rest.v3.beans.SearchedArtifact v3) {
        return SearchedArtifact.builder()
                .createdBy(v3.getCreatedBy())
                .createdOn(v3.getCreatedOn())
                .description(v3.getDescription())
                .groupId(v3.getGroupId())
                .id(v3.getId())
                .labels(v3.getLabels())
                .modifiedBy(v3.getModifiedBy())
                .modifiedOn(v3.getModifiedOn())
                .name(v3.getName())
                .state(v3.getState())
                .type(v3.getType())
                .build();
    }

    private static List<SearchedArtifact> fromV3_SearchedArtifactList(
            List<io.apicurio.registry.rest.v3.beans.SearchedArtifact> v3) {
        if (v3 == null) {
            return null;
        }
        return v3.stream().map(item -> {
            var v2 = fromV3(item);
            return v2;
        }).collect(Collectors.toList());
    }

    public static ArtifactSearchResults fromV3(
            io.apicurio.registry.rest.v3.beans.ArtifactSearchResults v3) {
        return ArtifactSearchResults.builder()
                .artifacts(fromV3_SearchedArtifactList(v3.getArtifacts()))
                .count(v3.getCount())
                .build();
    }

    public static UserInfo fromV3(io.apicurio.registry.rest.v3.beans.UserInfo v3) {
        return UserInfo.builder()
                .admin(v3.getAdmin())
                .developer(v3.getDeveloper())
                .displayName(v3.getDisplayName())
                .username(v3.getUsername())
                .viewer(v3.getViewer())
                .build();
    }

    public static ArtifactMetaData fromV3(
            io.apicurio.registry.rest.v3.beans.ArtifactMetaData v3) {
        return ArtifactMetaData.builder()
                .contentId(v3.getContentId())
                .createdBy(v3.getCreatedBy())
                .createdOn(v3.getCreatedOn())
                .description(v3.getDescription())
                .globalId(v3.getGlobalId())
                .groupId(v3.getGroupId())
                .id(v3.getId())
                .labels(v3.getLabels())
                .modifiedBy(v3.getModifiedBy())
                .modifiedOn(v3.getModifiedOn())
                .name(v3.getName())
                .properties(v3.getProperties())
                .references(fromV3(v3.getReferences()))
                .state(v3.getState())
                .type(v3.getType())
                .version(v3.getVersion())
                .build();
    }

    public static io.apicurio.registry.rest.v3.beans.ArtifactContent toV3(ArtifactContent data) {
        return io.apicurio.registry.rest.v3.beans.ArtifactContent.builder()
                .content(data.getContent())
                .references(toV3_ArtifactReferenceList(data.getReferences()))
                .build();
    }

    public static IfExists toV3(io.apicurio.registry.rest.v2.beans.IfExists ifExists) {
        if (ifExists == null) {
            return IfExists.FAIL;
        }
        switch (ifExists) {
            case FAIL:
                return IfExists.FAIL;
            case RETURN:
                return IfExists.RETURN;
            case RETURN_OR_UPDATE:
                return IfExists.RETURN_OR_UPDATE;
            case UPDATE:
                return IfExists.UPDATE;
        }
        return IfExists.FAIL;
    }

    private static SearchedVersion fromV3(io.apicurio.registry.rest.v3.beans.SearchedVersion v3) {
        return SearchedVersion.builder()
                .contentId(v3.getContentId())
                .createdBy(v3.getCreatedBy())
                .createdOn(v3.getCreatedOn())
                .description(v3.getDescription())
                .globalId(v3.getGlobalId())
                .labels(v3.getLabels())
                .name(v3.getName())
                .properties(v3.getProperties())
                .references(fromV3_ArtifactReferenceList(v3.getReferences()))
                .state(v3.getState())
                .type(v3.getType())
                .version(v3.getVersion())
                .build();
    }

    private static List<SearchedVersion> fromV3_SearchedVersionList(
            List<io.apicurio.registry.rest.v3.beans.SearchedVersion> v3) {
        if (v3 == null) {
            return null;
        }
        return v3.stream().map(item -> {
            var v2 = fromV3(item);
            return v2;
        }).collect(Collectors.toList());
    }

    public static VersionSearchResults fromV3(
            io.apicurio.registry.rest.v3.beans.VersionSearchResults v3) {
        return VersionSearchResults.builder()
                .count(v3.getCount())
                .versions(fromV3_SearchedVersionList(v3.getVersions()))
                .build();
    }

    public static ArtifactOwner fromV3(io.apicurio.registry.rest.v3.beans.ArtifactOwner v3) {
        return ArtifactOwner.builder()
                .owner(v3.getOwner())
                .build();
    }

    public static io.apicurio.registry.rest.v3.beans.ArtifactOwner toV3(@NotNull ArtifactOwner v2) {
        return io.apicurio.registry.rest.v3.beans.ArtifactOwner.builder()
                .owner(v2.getOwner())
                .build();
    }

    public static GroupMetaData fromV3(io.apicurio.registry.rest.v3.beans.GroupMetaData v3) {
        return GroupMetaData.builder()
                .createdBy(v3.getCreatedBy())
                .createdOn(v3.getCreatedOn())
                .description(v3.getDescription())
                .id(v3.getId())
                .modifiedBy(v3.getModifiedBy())
                .modifiedOn(v3.getModifiedOn())
                .properties(v3.getProperties())
                .build();
    }

    private static SearchedGroup fromV3(io.apicurio.registry.rest.v3.beans.SearchedGroup v3) {
        return SearchedGroup.builder()
                .createdBy(v3.getCreatedBy())
                .createdOn(v3.getCreatedOn())
                .description(v3.getDescription())
                .id(v3.getId())
                .modifiedBy(v3.getModifiedBy())
                .modifiedOn(v3.getModifiedOn())
                .build();
    }

    private static List<SearchedGroup> fromV3_SearchedGroupList(
            List<io.apicurio.registry.rest.v3.beans.SearchedGroup> v3) {
        if (v3 == null) {
            return null;
        }
        return v3.stream().map(item -> {
            var v2 = fromV3(item);
            return v2;
        }).collect(Collectors.toList());
    }

    public static GroupSearchResults fromV3(
            io.apicurio.registry.rest.v3.beans.GroupSearchResults v3) {
        return GroupSearchResults.builder()
                .count(v3.getCount())
                .groups(V2ApiUtil.fromV3_SearchedGroupList(v3.getGroups()))
                .build();
    }

    public static CreateGroupMetaData toV3(
            io.apicurio.registry.rest.v2.beans.CreateGroupMetaData v2) {
        return CreateGroupMetaData.builder()
                .description(v2.getDescription())
                .id(v2.getId())
                .properties(v2.getProperties())
                .build();
    }

    public static Comment fromV3(io.apicurio.registry.rest.v3.beans.Comment v3) {
        return Comment.builder()
                .commentId(v3.getCommentId())
                .createdBy(v3.getCreatedBy())
                .createdOn(v3.getCreatedOn())
                .value(v3.getValue())
                .build();
    }

    public static List<Comment> fromV3_CommentList(
            List<io.apicurio.registry.rest.v3.beans.Comment> v3) {
        if (v3 == null) {
            return null;
        }
        return v3.stream().map(item -> {
            var v2 = fromV3(item);
            return v2;
        }).collect(Collectors.toList());
    }

    public static NewComment toV3(io.apicurio.registry.rest.v2.beans.NewComment v2) {
        return NewComment.builder()
                .value(v2.getValue())
                .build();
    }

}
