package io.apicurio.registry.storage.dto;

import io.apicurio.registry.storage.StorageEventType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

/**
 * Data transfer object representing the user-editable subset of a webhook subscription. Fields left null are
 * not modified by an update. The subscription ID, creator, and timestamps are managed by the storage layer
 * and therefore not part of this DTO.
 * <p>
 * The {@code secret} field is excluded from {@link #toString()} so it cannot leak into logs.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class EditableWebhookSubscriptionDto {

    private String name;
    private String endpointUrl;
    private Set<StorageEventType> eventTypes;
    private String groupFilter;
    private String artifactIdFilter;
    private Boolean enabled;
    @ToString.Exclude
    private String secret;
}
