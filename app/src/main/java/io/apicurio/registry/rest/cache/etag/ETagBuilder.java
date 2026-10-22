package io.apicurio.registry.rest.cache.etag;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.cache.etag.ETagKeys.ETagKey;
import io.apicurio.registry.rest.cache.etag.ETagKeys.ETagListKey;
import io.apicurio.registry.rest.cache.etag.ETagKeys.Key;
import jakarta.ws.rs.core.EntityTag;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.apicurio.registry.utils.StringUtil.contains;
import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;

/**
 * IMPORTANT: Incorrect usage of eTag values can lead to stale data being served to clients.
 * Please read this carefully:
 * <p>
 * eTag represents an identifier of a specific version of a resource. Whenever the resource changes,
 * the eTag MUST also change. If the resource does not change, the eTag SHOULD remain the same.
 * <p>
 * Resource means content returned by and endpoint, *including* any variations based on query parameters.
 * This means that if an endpoint returns different content based on query parameters, those
 * parameters MUST be included in the eTag calculation. However, a different endpoint MAY return different
 * eTag even if it returns the same content. However, we should strive to keep eTag values consistent
 * for the same content "type" so we only have a small set of ways to compute them.
 * <p>
 * Note that Varnish also caches response headers, so the ETag MUST change if a relevant response header would change.
 */
public class ETagBuilder {

    private final SortedMap<String, String> parts = new TreeMap<>();

    // We cannot use ",", because it is already used as a standard separator
    // for multiple ETags in the same header.
    // TODO: Add some kind of escaping if it causes issues.
    private <T> String getValue(Key<T> key, T value) {
        var valueString = valueOf(value);
        if (contains(valueString, "=;+,")) {
            throw new IllegalArgumentException("ETag values cannot contain '=;+,' characters. " +
                    "Got '" + valueString + "' for key '" + key.getKey() + "'.");
        }
        return valueString;
    }

    public <T> ETagBuilder with(ETagKey<T> key, T value) {
        requireNonNull(key, "key");
        if (value != null) {
            parts.put(key.getKey(), getValue(key, value));
        }
        return this;
    }

    /**
     * IMPORTANT: The order of values in the list will affect the generated ETag. Make sure to always provide values in the same order.
     */
    public <T> ETagBuilder with(ETagListKey<T> key, List<T> value) {
        requireNonNull(key, "key");
        if (value != null) {
            var valueString = value.stream()
                    .map(v -> getValue(key, v))
                    .collect(Collectors.joining("+"));
            parts.put(key.getKey(), valueString);
        }
        return this;
    }

    public ETagBuilder withRandom() {
        return with(ETagKeys.UUID, UUID.randomUUID());
    }

    private String buildRaw() {
        var sb = new StringBuilder();
        boolean first = true;
        for (var entry : parts.entrySet()) {
            if (!first) {
                sb.append(";");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    public EntityTag build() {
        return new EntityTag(buildRaw());
    }

    public EntityTag buildHashed() {
        return new EntityTag(ContentHandle.create(buildRaw()).getSha256Hash());
    }
}
