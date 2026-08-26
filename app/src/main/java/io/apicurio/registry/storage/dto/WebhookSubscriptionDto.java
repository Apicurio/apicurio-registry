package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Data transfer object representing a webhook subscription.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class WebhookSubscriptionDto {

    private String subscriptionId;
    private String endpointUrl;
    private List<String> eventTypes;
    private String groupFilter;
    private String artifactFilter;
    private String authType;
    private String authConfig;
    private boolean isEnabled;
    private String owner;
    private long createdOn;
    private String modifiedBy;
    private long modifiedOn;
}
