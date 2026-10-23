package io.apicurio.registry.rest.v3.impl;

import jakarta.enterprise.context.RequestScoped;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * What {@link WebhookRequestBodyReaderInterceptor} found wrong with a webhook subscription request body.
 * The interceptor never rejects a request; {@link WebhookSubscriptionOperations} does, after
 * authorization, so an unauthorized caller always gets 401 or 403 rather than a body error.
 */
@RequestScoped
public class WebhookRequestBodyContext {

    /** The body was larger than {@link WebhookRequestBodyReaderInterceptor#MAX_BODY_BYTES}. */
    @Getter
    @Setter
    private boolean tooLarge;

    /** The body was not a JSON object, or a field had the wrong type. */
    @Getter
    @Setter
    private boolean malformed;

    /** Top-level fields that the request bean does not declare. */
    @Getter
    @Setter
    private Set<String> unrecognizedFields = Set.of();
}
