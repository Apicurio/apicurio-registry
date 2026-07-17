package io.apicurio.registry.cli.utils;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.client.common.util.DateTimeConversions;
import io.apicurio.registry.client.util.LabelsUtil;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.v3.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.Comment;
import io.apicurio.registry.rest.v3.beans.GroupMetaData;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.Rule;
import io.apicurio.registry.rest.v3.beans.SearchedArtifact;
import io.apicurio.registry.rest.v3.beans.SearchedGroup;
import io.apicurio.registry.rest.v3.beans.SearchedVersion;
import io.apicurio.registry.rest.v3.beans.VersionMetaData;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

// The generic (non-bean-mapping) conversions have been moved to the Java SDK, see
// io.apicurio.registry.client.util.LabelsUtil and
// io.apicurio.registry.client.common.util.DateTimeConversions (#8644). The remaining
// methods here map generated SDK client models to this module's internal
// io.apicurio.registry.rest.v3.beans / io.apicurio.registry.types beans, which live in the
// apicurio-registry-common module. Those stay in the CLI: moving them to the SDK would
// require the (standalone, dependency-light) Java SDK to depend on apicurio-registry-common,
// which is the wrong direction (common/app depend on the SDK, not vice versa).
public final class Conversions {

    private static final String API_LABEL_SEPARATOR = ":";

    private Conversions() {
    }

    // Moved to the Java SDK, see io.apicurio.registry.client.util.LabelsUtil (#8644).
    public static Map<String, String> convert(io.apicurio.registry.rest.client.models.Labels labels) {
        return LabelsUtil.toMap(labels);
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

    public static Comment convert(io.apicurio.registry.rest.client.models.Comment comment) {
        return Comment.builder()
                .commentId(comment.getCommentId())
                .value(comment.getValue())
                .owner(comment.getOwner())
                .createdOn(ofNullable(comment.getCreatedOn()).map(Conversions::convert).orElse(null))
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

    // Moved to the Java SDK, see io.apicurio.registry.client.common.util.DateTimeConversions (#8644).
    public static String convertToString(OffsetDateTime ts) {

        if (ts == null) {
            return "";
        }
        return DateTimeConversions.toIsoLocalDateTimeString(ts);
    }

    // Moved to the Java SDK, see io.apicurio.registry.client.common.util.DateTimeConversions (#8644).
    public static String convertToString(Date ts) {

               if (ts == null) {
            return "";
        }
        return DateTimeConversions.toIsoLocalDateTimeString(ts);
    }

    // Moved to the Java SDK, see io.apicurio.registry.client.common.util.DateTimeConversions (#8644).
    public static Date convert(OffsetDateTime ts) {
        return DateTimeConversions.toDate(ts);
    }

    // Moved to the Java SDK, see io.apicurio.registry.client.util.LabelsUtil (#8644).
    public static String convertToString(Labels labels) {
        return LabelsUtil.toDisplayString(labels);
    }

    // Moved to the Java SDK, see io.apicurio.registry.client.util.LabelsUtil (#8644).
    public static String convertToString(Map<String, ?> labels) {
        return LabelsUtil.toDisplayString(labels);
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

    // Moved to the Java SDK, see io.apicurio.registry.client.util.LabelsUtil (#8644).
    public static Map<String, String> parseLabels(final List<String> labels) {
        return LabelsUtil.parseLabels(labels);
    }

    public static String[] convertLabelsForApi(final List<String> labels) {
        return labels.stream()
                .map(Conversions::convertLabelForApi)
                .toArray(String[]::new);
    }

    private static String convertLabelForApi(final String label) {
        final Map.Entry<String, String> parts = LabelsUtil.splitLabel(label);
        if (parts.getKey().contains(API_LABEL_SEPARATOR)) {
            throw new CliException("Label key '" + parts.getKey() + "' must not contain colons. Colons are reserved by the REST API.",
                    CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        if (parts.getValue().isEmpty()) {
            return parts.getKey();
        }
        return parts.getKey() + API_LABEL_SEPARATOR + parts.getValue();
    }
}
