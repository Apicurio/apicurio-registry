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
