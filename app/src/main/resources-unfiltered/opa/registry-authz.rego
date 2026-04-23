package registry.authz

import rego.v1

default allow := false

# Admin users can do everything
allow if {
	user_has_role(input.user, "admin")
}

# Check explicit permission grants
allow if {
	some grant in data.grants
	principal_matches(grant, input.user)
	operation_matches(grant, input.operation)
	resource_matches(grant, input.resource_type, input.resource_name)
}

# Batch evaluation: filter a list of resources the user can access
filter_resources[resource] if {
	some resource in input.resources
	allow_resource(input.user, input.operation, resource.resource_type, resource.resource_name)
}

allow_resource(user, operation, resource_type, resource_name) if {
	user_has_role(user, "admin")
}

allow_resource(user, operation, resource_type, resource_name) if {
	some grant in data.grants
	principal_matches(grant, user)
	operation_matches(grant, operation)
	resource_matches(grant, resource_type, resource_name)
}

# Helper: check if user has a role
user_has_role(user, role) if {
	some r in data.roles[user]
	r == role
}

# Helper: check if principal matches the grant
principal_matches(grant, user) if {
	grant.principal == user
}

principal_matches(grant, user) if {
	grant.principal_role != ""
	user_has_role(user, grant.principal_role)
}

# Helper: check if operation matches (with implies hierarchy)
operation_matches(grant, operation) if {
	grant.operation == operation
}

operation_matches(grant, operation) if {
	grant.operation == "admin"
	operation in {"read", "write", "admin"}
}

operation_matches(grant, operation) if {
	grant.operation == "write"
	operation in {"read", "write"}
}

# Helper: check if resource matches
resource_matches(grant, resource_type, resource_name) if {
	grant.resource_type == resource_type
	grant.resource_pattern == "*"
}

resource_matches(grant, resource_type, resource_name) if {
	grant.resource_type == resource_type
	grant.resource_pattern_type == "exact"
	grant.resource_pattern == resource_name
}

resource_matches(grant, resource_type, resource_name) if {
	grant.resource_type == resource_type
	grant.resource_pattern_type == "prefix"
	startswith(resource_name, grant.resource_pattern)
}
