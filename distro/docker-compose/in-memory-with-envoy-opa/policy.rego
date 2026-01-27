package envoy.authz

import future.keywords.contains
import future.keywords.if
import future.keywords.in

# Default deny - explicit allow required
default allow := false

# Extract JWT payload from Envoy metadata
jwt_payload := payload if {
    payload := input.attributes.metadataContext.filterMetadata["envoy.filters.http.jwt_authn"]["jwt_payload"]
}

# Extract roles from JWT realm_access.roles
user_roles := roles if {
    roles := jwt_payload.realm_access.roles
} else := []

# Extract username from JWT
username := jwt_payload.preferred_username

# Extract email from JWT
email := jwt_payload.email

# Extract HTTP method
http_method := input.attributes.request.http.method

# Extract request path
request_path := input.attributes.request.http.path

# Check if user has a specific role
has_role(role) if {
    role in user_roles
}

# Allow anonymous access to system endpoints
allow if {
    startswith(request_path, "/apis/registry/v3/system/")
}

allow if {
    startswith(request_path, "/apis/registry/v2/system/")
}

# Admin users can do anything
allow if {
    has_role("sr-admin")
}

# Developer users can read and write
allow if {
    has_role("sr-developer")
}

# Read-only users can only GET
allow if {
    has_role("sr-readonly")
    http_method == "GET"
}

# Read-only users trying to write should be denied
deny_reason := "Read-only users cannot modify resources" if {
    has_role("sr-readonly")
    http_method != "GET"
}

# No valid roles found
deny_reason := "User does not have required roles" if {
    not has_role("sr-admin")
    not has_role("sr-developer")
    not has_role("sr-readonly")
}

# Note: Identity headers (X-Forwarded-User, X-Forwarded-Email, X-Forwarded-Groups)
# are injected by Envoy's JWT filter, not by OPA. OPA only makes the allow/deny decision.
