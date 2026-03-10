package io.apicurio.registry.rest.cache.headers;

import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Duration;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

@AllArgsConstructor
@Builder
public class SurrogateControlHttpHeader implements HttpHeader {

    // @formatter:off
    /*
     * `immutable`       = Not used by Varnish, but we send it to mirror the browser Cache-Control header.
     *                     Browsers would otherwise periodically revalidate on page reload even within the max-age period,
     *                     using ETags or Last-Modified.
     * `must-revalidate` = Not used by Varnish either, but we send it to mirror the browser Cache-Control header.
     *                     Browsers MUST revalidate after the max-age period, which prevents stale content.
     *                     Varnish has a separate concept of "grace" period, which controls serving of stale content.
     *                     See README.md for details.
     *                     TODO: Configure Varnish to look for `must-revalidate`?
     */
    // @formatter:on
    private static final String IMMUTABLE = "public, max-age=%s, immutable";
    private static final String MUTABLE = "public, max-age=%s, must-revalidate";

    private final Duration expiration;
    private final boolean immutable;

    @Override
    public String key() {
        return "Surrogate-Control";
    }

    @Override
    public String value() {
        var template = immutable ? IMMUTABLE : MUTABLE;
        requireNonNull(expiration, "expiration");
        return format(template, expiration.getSeconds());
    }
}
