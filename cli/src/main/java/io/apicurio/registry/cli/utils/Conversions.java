package io.apicurio.registry.cli.utils;

import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.v3.beans.GroupMetaData;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.SearchedGroup;

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
}
