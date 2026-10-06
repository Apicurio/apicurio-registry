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
 * Data transfer object for a row in {@code webhook_delivery_logs}. This is work state, not an
 * attempt log: one row per (subscription, event) pair is updated in place rather than a new row
 * appended per attempt. {@code claimToken} is the fencing key; completion and retry updates are
 * conditional on it matching the current value.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class WebhookDeliveryDto {

    private String deliveryId;
    private String subscriptionId;
    private String eventRowId;
    private WebhookDeliveryStatus status;
    private int attemptCount;
    /** Epoch-millisecond due time for the next delivery attempt. */
    private Long nextAttemptAt;
    /** Fresh per lease; used as the fencing key for conditional updates. */
    private String claimToken;
    private Long leaseUntil;
    private Long lastAttemptAt;
    private Integer httpStatusCode;
    /** Bounded, sanitized error code; never raw exception text. */
    private String errorCode;
    private long createdOn;
    private long updatedOn;
    /** Epoch-millisecond timestamp when the delivery reached a terminal state; retention anchor. */
    private Long completedOn;
}
