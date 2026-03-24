vcl 4.1;

import std;

# =============================================================================
# CONFIGURATION
# =============================================================================

# IMPORTANT: Production Configuration
# This VCL is configured for development/testing and exposes internal cache headers.
# For production deployments, search for "PRODUCTION:" comments and follow instructions
# to hide internal headers from clients.

# Backend configuration
# For docker-compose: point to registry container on port 8081
# For dev mode: point to host.docker.internal:8080
backend registry {
    .host = "registry";
    .port = "8080";
    .connect_timeout = 5s;
    .first_byte_timeout = 60s;
    .between_bytes_timeout = 10s;

    # Health check
    .probe = {
        .url = "/health/live";
        .interval = 5s;
        .timeout = 2s;
        .window = 5;
        .threshold = 3;
    }
}

# ACL for cache purging
# Allows localhost and Docker networks (for testing/development)
# IMPORTANT: Restrict this in production!
acl purge_acl {
    "localhost";
    "127.0.0.1";
    "::1";
    # Docker default bridge network
    "172.16.0.0"/12;
    # Docker compose default network range
    "192.168.0.0"/16;
}

# Called when a request is received
sub vcl_recv {
    # Handle PURGE requests FIRST (before method filtering)
    # IMPORTANT: For production, add ACL restriction!
    if (req.method == "PURGE") {
        # Uncomment for production ACL restriction:
        # if (!client.ip ~ purge_acl) {
        #     return (synth(405, "Not allowed"));
        # }
        return (purge);
    }

    # Only cache GET and HEAD requests
    if (req.method != "GET" && req.method != "HEAD") {
        # Pass through all write operations (POST, PUT, DELETE)
        return (pass);
    }

    # Normalize query parameters for consistent cache keys
    # Sorts parameters alphabetically: ?b=2&a=1 -> ?a=1&b=2
    if (req.url ~ "\?") {
        set req.url = std.querysort(req.url);
    }

    # Remove any cookies (we don't use them for API caching)
    unset req.http.Cookie;

    # Allow BAN requests from localhost
    if (req.method == "BAN") {
        if (!client.ip ~ purge_acl) {
            return (synth(405, "Not allowed"));
        }
        ban("req.url ~ " + req.url);
        return (synth(200, "Banned"));
    }

    # Continue to cache lookup
    return (hash);
}


# Called to generate the cache key
sub vcl_hash {
    # Include URL in cache key
    hash_data(req.url);

    # Include Host header in cache key
    if (req.http.Host) {
        hash_data(req.http.Host);
    } else {
        hash_data(server.ip);
    }

    # For Phase 2+: Include Authorization header if present
    # if (req.http.Authorization) {
    #     hash_data(req.http.Authorization);
    # }

    return (lookup);
}

# Called before making a backend request
sub vcl_backend_fetch {
    # Varnish automatically handles ETag-based revalidation:
    #
    # Within keep window (obj.ttl < 0, obj.keep > 0):
    #   - Object is stale but still in cache
    #   - Varnish sends If-None-Match with cached ETag
    #   - Backend can return 304 Not Modified (bandwidth-efficient)
    #
    # Beyond keep window (obj.keep < 0):
    #   - Varnish stops using If-None-Match for revalidation
    #   - Next request performs a full fetch (no conditional request)
    #   - Object becomes eligible for eviction but may remain in cache
    #   - Ensures absolute freshness periodically
}

# Called after a backend response is received
sub vcl_backend_response {
    # Use Surrogate-Control for Varnish-specific caching directives
    # This allows different caching behavior for Varnish vs browsers
    # If Surrogate-Control is present, use it; otherwise fall back to Cache-Control

    if (beresp.http.Surrogate-Control) {
        # Parse max-age from Surrogate-Control for TTL
        if (beresp.http.Surrogate-Control ~ "max-age=([0-9]+)") {
            set beresp.ttl = std.duration(
                regsub(beresp.http.Surrogate-Control, ".*max-age=([0-9]+).*", "\1") + "s",
                0s
            );
        }

        # Check for no-store directive in Surrogate-Control
        if (beresp.http.Surrogate-Control ~ "no-store") {
            set beresp.uncacheable = true;
            unset beresp.http.Surrogate-Control;
            return (deliver);
        }

        # PRODUCTION: Uncomment to hide Surrogate-Control from clients
        # unset beresp.http.Surrogate-Control;
    } else if (!beresp.http.Cache-Control) {
        # No Surrogate-Control and no Cache-Control - don't cache
        set beresp.uncacheable = true;
        return (deliver);
    } else {
        # No Surrogate-Control, fall back to Cache-Control
        # Check if backend explicitly says not to cache
        if (beresp.http.Cache-Control ~ "no-cache" ||
            beresp.http.Cache-Control ~ "no-store" ||
            beresp.http.Cache-Control ~ "private") {
            set beresp.uncacheable = true;
            return (deliver);
        }

        # Varnish automatically respects Cache-Control max-age if Surrogate-Control not present
    }

    # Read X-Cache-Cacheability header to control conditional request (304) optimization window
    # MODERATE cacheability: keep = 3 * TTL (allows ETag-based revalidation for moderate period)
    # Other cacheability (HIGH, LOW, NONE): keep = 0s (no extended keep period)
    if (beresp.http.X-Cache-Cacheability == "MODERATE") {
        # For MODERATE cacheability, allow ETag-based revalidation for 3x the TTL
        set beresp.keep = beresp.ttl * 3;
    } else {
        # For HIGH, LOW, or NONE cacheability, disable extended keep period
        set beresp.keep = 0s;
    }

    # PRODUCTION: Uncomment to hide X-Cache-Cacheability from clients
    # unset beresp.http.X-Cache-Cacheability;

    # Disable grace mode to prevent stale-while-revalidate (background fetch)
    # We use ETag-based conditional requests for efficient revalidation instead
    # Grace would serve stale content while fetching in background, causing test failures
    set beresp.grace = 0s;

    # Remove Set-Cookie from responses (we don't use cookies)
    unset beresp.http.Set-Cookie;

    # Store the backend response in cache
    return (deliver);
}

# Called before delivering response to client
sub vcl_deliver {
    # Add debug headers to show cache hit/miss
    if (obj.hits > 0) {
        set resp.http.X-Debug-Cache = "HIT";
        set resp.http.X-Debug-Cache-Hits = obj.hits;
    } else {
        set resp.http.X-Debug-Cache = "MISS";
    }

    # Add cache age for debugging
    set resp.http.X-Debug-Cache-Age = obj.age;

    # PRODUCTION: Uncomment to hide debug headers from clients
    # unset resp.http.X-Debug-Cache;
    # unset resp.http.X-Debug-Cache-Hits;
    # unset resp.http.X-Debug-Cache-Age;

    return (deliver);
}

# Called when delivering a synthetic response (error, redirect, etc.)
sub vcl_synth {
    # Handle PURGE response
    if (resp.status == 200 && req.method == "PURGE") {
        set resp.http.Content-Type = "text/plain";
        synthetic("Purged: " + req.url);
        return (deliver);
    }

    # Handle BAN response
    if (resp.status == 200 && req.method == "BAN") {
        set resp.http.Content-Type = "text/plain";
        synthetic("Banned: " + req.url);
        return (deliver);
    }

    return (deliver);
}

# Called when backend fetch fails
sub vcl_backend_error {
    # Try to serve stale content if available (grace mode)
    # This happens automatically if grace is set

    # Otherwise return error
    set beresp.http.Content-Type = "text/plain";
    set beresp.status = 503;
    synthetic("Backend fetch failed");
    return (deliver);
}
