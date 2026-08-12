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

import java.util.Date;
import java.util.Set;

/**
 * Data transfer object representing a webhook subscription. A subscription describes an HTTP endpoint that
 * should receive CloudEvents for a set of storage event types, optionally narrowed by group and artifact ID
 * filters.
 * <p>
 * The {@code secret} field is used to compute the HMAC signature sent with each delivery. It is deliberately
 * excluded from {@link #toString()} so it cannot leak into logs.
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
    private String name;
    private String endpointUrl;
    private Set<StorageEventType> eventTypes;
    private String groupFilter;
    private String artifactIdFilter;
    @Builder.Default
    private boolean enabled = true;
    @ToString.Exclude
    private String secret;
    private String createdBy;
    private Date createdOn;
    private Date modifiedOn;
}
