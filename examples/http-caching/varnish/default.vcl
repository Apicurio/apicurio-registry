vcl 4.1;

import std;

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

# Called after a backend response is received
sub vcl_backend_response {
    # Only cache responses with Cache-Control header
    if (!beresp.http.Cache-Control) {
        set beresp.uncacheable = true;
        return (deliver);
    }

    # Check if backend explicitly says not to cache
    if (beresp.http.Cache-Control ~ "no-cache" ||
        beresp.http.Cache-Control ~ "no-store" ||
        beresp.http.Cache-Control ~ "private") {
        set beresp.uncacheable = true;
        return (deliver);
    }

    # Enable grace mode: serve stale content for 1 hour if backend is down
    # This improves availability during backend failures
    set beresp.grace = 1h;

    # For immutable content (Phase 1), cache for maximum duration
    if (beresp.http.Cache-Control ~ "immutable") {
        # Cache for 1 year
        set beresp.ttl = 365d;
    }

    # Remove Set-Cookie from responses (we don't use cookies)
    unset beresp.http.Set-Cookie;

    # Store the backend response in cache
    return (deliver);
}

# Called before delivering response to client
sub vcl_deliver {
    # Add debug header to show cache hit/miss
    # IMPORTANT: Remove in production or add conditional based on debug flag
    if (obj.hits > 0) {
        set resp.http.X-Cache = "HIT";
        set resp.http.X-Cache-Hits = obj.hits;
    } else {
        set resp.http.X-Cache = "MISS";
    }

    # Add cache age for debugging
    set resp.http.X-Cache-Age = obj.age;

    # For production, remove internal headers:
    # unset resp.http.X-Cache;
    # unset resp.http.X-Cache-Hits;
    # unset resp.http.X-Cache-Age;

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
