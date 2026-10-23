package io.apicurio.authz;

import io.kroxylicious.identity.Principal;

/**
 * A role held by the subject. A subject may carry any number of roles; grants with a
 * {@code principal_role} field are matched against them.
 */
public record RolePrincipal(String name) implements Principal {
}
