package io.apicurio.authz;

import io.kroxylicious.identity.Principal;

/**
 * A role held by the subject, for example an identity provider role. A subject may carry any
 * number of roles. A top-level class, so rules files can import it
 * ({@code from io.apicurio.authz import Role;}).
 */
public record Role(String name) implements Principal {
}
