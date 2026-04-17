package io.apicurio.registry.cli.utils;

import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.v3.beans.Rule;
import io.apicurio.registry.rest.v3.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.GroupMetaData;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.SearchedArtifact;
import io.apicurio.registry.rest.v3.beans.SearchedGroup;
import io.apicurio.registry.rest.v3.beans.SearchedVersion;
import io.apicurio.registry.rest.v3.beans.VersionMetaData;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

// TODO: Move some of these to the Java SDK.
public final class Conversions {

    private Conversions() {
    }

    public static Map<String, String> convert(io.apicurio.registry.rest.client.models.Labels labels) {
        return ofNullable(labels)
                .map(Labels::getAdditionalData)
                .map(x -> (Map<String, String>) (Map<String, ?>) x)
                .orElse(Map.of());
    }

    public static GroupMetaData convert(io.apicurio.registry.rest.client.models.GroupMetaData group) {
        return GroupMetaData.builder()
                .groupId(group.getGroupId())
                .description(group.getDescription())
                .createdOn(convert(group.getCreatedOn()))
                .owner(group.getOwner())
                .modifiedOn(convert(group.getModifiedOn()))
                .modifiedBy(group.getModifiedBy())
                .labels(convert(group.getLabels()))
                .build();
    }

    public static SearchedGroup convert(io.apicurio.registry.rest.client.models.SearchedGroup group) {
        return SearchedGroup.builder()
                .groupId(group.getGroupId())
                .description(group.getDescription())
                .createdOn(convert(group.getCreatedOn()))
                .owner(group.getOwner())
                .modifiedOn(convert(group.getModifiedOn()))
                .modifiedBy(group.getModifiedBy())
                .labels(convert(group.getLabels()))
                .build();
    }

    public static GroupSearchResults convert(io.apicurio.registry.rest.client.models.GroupSearchResults searchResults) {
        return GroupSearchResults.builder()
                .groups(searchResults.getGroups().stream()
                        .map(Conversions::convert)
                        .collect(Collectors.toList()))
                .count(searchResults.getCount())
                .build();
    }

    public static ArtifactMetaData convert(io.apicurio.registry.rest.client.models.ArtifactMetaData artifact) {
        return ArtifactMetaData.builder()
                .groupId(artifact.getGroupId())
                .artifactId(artifact.getArtifactId())
                .artifactType(artifact.getArtifactType())
                .name(artifact.getName())
                .description(artifact.getDescription())
                .createdOn(convert(artifact.getCreatedOn()))
                .owner(artifact.getOwner())
                .modifiedOn(convert(artifact.getModifiedOn()))
                .modifiedBy(artifact.getModifiedBy())
                .labels(convert(artifact.getLabels()))
                .build();
    }

    public static SearchedArtifact convert(io.apicurio.registry.rest.client.models.SearchedArtifact artifact) {
        return SearchedArtifact.builder()
                .groupId(artifact.getGroupId())
                .artifactId(artifact.getArtifactId())
                .artifactType(artifact.getArtifactType())
                .name(artifact.getName())
                .description(artifact.getDescription())
                .createdOn(convert(artifact.getCreatedOn()))
                .owner(artifact.getOwner())
                .modifiedOn(convert(artifact.getModifiedOn()))
                .modifiedBy(artifact.getModifiedBy())
                .labels(convert(artifact.getLabels()))
                .build();
    }

    public static ArtifactSearchResults convert(io.apicurio.registry.rest.client.models.ArtifactSearchResults searchResults) {
        return ArtifactSearchResults.builder()
                .artifacts(searchResults.getArtifacts().stream()
                        .map(Conversions::convert)
                        .collect(Collectors.toList()))
                .count(searchResults.getCount())
                .build();
    }

    public static VersionMetaData convert(io.apicurio.registry.rest.client.models.VersionMetaData version) {
        return VersionMetaData.builder()
                .groupId(version.getGroupId())
                .artifactId(version.getArtifactId())
                .version(version.getVersion())
                .name(version.getName())
                .description(version.getDescription())
                .artifactType(version.getArtifactType())
                .state(convertState(version.getState()))
                .globalId(version.getGlobalId())
                .contentId(version.getContentId())
                .createdOn(convert(version.getCreatedOn()))
                .owner(version.getOwner())
                .modifiedOn(convert(version.getModifiedOn()))
                .modifiedBy(version.getModifiedBy())
                .labels(convert(version.getLabels()))
                .build();
    }

    public static SearchedVersion convert(io.apicurio.registry.rest.client.models.SearchedVersion version) {
        return SearchedVersion.builder()
                .groupId(version.getGroupId())
                .artifactId(version.getArtifactId())
                .version(version.getVersion())
                .name(version.getName())
                .description(version.getDescription())
                .artifactType(version.getArtifactType())
                .state(convertState(version.getState()))
                .globalId(version.getGlobalId())
                .contentId(version.getContentId())
                .createdOn(convert(version.getCreatedOn()))
                .owner(version.getOwner())
                .modifiedOn(convert(version.getModifiedOn()))
                .modifiedBy(version.getModifiedBy())
                .labels(convert(version.getLabels()))
                .build();
    }

    public static VersionSearchResults convert(io.apicurio.registry.rest.client.models.VersionSearchResults searchResults) {
        return VersionSearchResults.builder()
                .versions(searchResults.getVersions().stream()
                        .map(Conversions::convert)
                        .collect(Collectors.toList()))
                .count(searchResults.getCount())
                .build();
    }

    public static Rule convert(io.apicurio.registry.rest.client.models.Rule rule) {
        return Rule.builder()
                .ruleType(ofNullable(rule.getRuleType())
                        .map(rt -> io.apicurio.registry.types.RuleType.fromValue(rt.getValue()))
                        .orElse(null))
                .config(rule.getConfig())
                .build();
    }

    public static String convertToString(OffsetDateTime ts) {
        return ts.atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static String convertToString(Date ts) {
        return ts.toInstant()
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static Date convert(OffsetDateTime ts) {
        return Date.from(ts.toInstant());
    }

    public static String convertToString(Labels labels) {
        return ofNullable(labels)
                .map(Labels::getAdditionalData)
                .map(Conversions::convertToString)
                .orElse("");
    }

    public static String convertToString(Map<String, ?> labels) {
        return labels.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    public static io.apicurio.registry.types.VersionState convertState(
            io.apicurio.registry.rest.client.models.VersionState state) {
        return ofNullable(state)
                .map(s -> io.apicurio.registry.types.VersionState.fromValue(s.getValue()))
                .orElse(null);
    }

    public static String convertToString(io.apicurio.registry.types.VersionState state) {
        return ofNullable(state).map(io.apicurio.registry.types.VersionState::value).orElse("");
    }

    public static String convertToString(Long value) {
        return ofNullable(value).map(String::valueOf).orElse("");
    }
}
