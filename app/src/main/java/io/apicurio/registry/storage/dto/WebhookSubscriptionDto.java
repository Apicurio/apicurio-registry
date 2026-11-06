package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class WebhookSubscriptionDto {

    private String subscriptionId;
    private String name;
    private String ownerId;
    private String endpointUrl;
    /** JSON array of event type strings; empty array means all supported types. */
    private String eventTypes;
    private String groupFilter;
    private String artifactIdFilter;
    private String artifactTypeFilter;
    private boolean enabled;
    /** Epoch-millisecond soft-deletion marker; null when the subscription is active. */
    private Long deletedOn;
    /** Optimistic-concurrency counter, incremented on every update. */
    private long revision;
    /** Reference name for the signing secret, never the secret value itself. */
    private String signingSecretRef;
    private long createdOn;
    private long modifiedOn;
}
