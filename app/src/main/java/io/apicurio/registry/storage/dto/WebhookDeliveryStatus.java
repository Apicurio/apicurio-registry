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

/**
 * The six terminal and non-terminal states of a webhook delivery row in
 * {@code webhook_delivery_logs}. One row per (subscription, event) pair is updated in place as
 * delivery progresses; a new row is never appended per attempt.
 */
public enum WebhookDeliveryStatus {
    PENDING,
    IN_FLIGHT,
    RETRYING,
    DELIVERED,
    FAILED,
    CANCELLED
}
