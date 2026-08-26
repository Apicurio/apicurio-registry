package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Search results for webhook subscriptions.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class WebhookSubscriptionSearchResultsDto {

    private List<WebhookSubscriptionDto> webhookSubscriptions;
    private int count;
}
