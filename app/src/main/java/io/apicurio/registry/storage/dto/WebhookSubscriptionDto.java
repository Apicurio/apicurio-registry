/*
 * Copyright 2025 Red Hat
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

package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Data transfer object for a row in {@code webhook_subscriptions}. Represents who wants to be
 * notified and which events they want. {@code signingSecretRef} holds a secret name, never a
 * plaintext secret value.
 */
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
