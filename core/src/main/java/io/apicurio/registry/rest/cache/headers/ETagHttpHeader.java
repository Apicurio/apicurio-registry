package io.apicurio.registry.rest.cache.headers;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.ext.RuntimeDelegate;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.stream.Collectors;

import static io.apicurio.registry.utils.StringUtil.isEmpty;
import static java.util.Arrays.stream;
import static lombok.AccessLevel.PRIVATE;

/**
 * ETag header for HTTP caching.
 * <p>
 * Example: ETag: "1234567890"
 */
@AllArgsConstructor(access = PRIVATE)
@Builder
public class ETagHttpHeader implements HttpHeader {

    private final EntityTag etag;

    @Override
    public String key() {
        return "ETag";
    }

    @Override
    public String value() {
        return RuntimeDelegate.getInstance().createHeaderDelegate(EntityTag.class).toString(etag);
    }

    /**
     * Checks if the given If-None-Match header value matches this ETag.
     * Properly handles:
     * <ul>
     *   <li>Weak ETags (W/"...")</li>
     *   <li>Multiple comma-separated ETags</li>
     *   <li>Wildcard "&#42;"</li>
     *   <li>Quote stripping</li>
     * </ul>
     * <p>
     * According to RFC 7232 section 2.3.2, for If-None-Match comparison,
     * weak comparison is used: ETags match if their opaque-tags match,
     * regardless of whether either or both are weak.
     *
     * @param ifNoneMatch the If-None-Match header value from the request
     * @return true if the ETag matches, false otherwise
     */
    public boolean matches(String ifNoneMatch) {
        if (isEmpty(ifNoneMatch)) {
            return false;
        }
        // Wildcard matches everything:
        if ("*".equals(ifNoneMatch.trim())) {
            return true;
        }
        // Weak comparison per RFC 7232 ignores the weak/strong distinction.
        return parseIfNoneMatch(ifNoneMatch).stream()
                .anyMatch(requestETag -> requestETag.getValue().equals(etag.getValue()));
    }

    /**
     * Parses an If-None-Match header value into a list of ETags.
     * Handles multiple comma-separated ETags and weak ETags using JAX-RS's EntityTag delegate.
     *
     * @param ifNoneMatch the If-None-Match header value
     * @return list of parsed ETags
     */
    private List<EntityTag> parseIfNoneMatch(String ifNoneMatch) {
        var delegate = RuntimeDelegate.getInstance().createHeaderDelegate(EntityTag.class);
        return stream(ifNoneMatch.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(delegate::fromString)
                .collect(Collectors.toList());
    }
}
