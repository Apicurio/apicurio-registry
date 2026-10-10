package io.apicurio.authz;

import io.kroxylicious.identity.Principal;
import io.kroxylicious.identity.SingularPrincipal;

/**
 * The authenticated user. A subject carries at most one {@code User}, which is what grants with a
 * {@code principal} field are matched against.
 */
@SingularPrincipal
public record User(String name) implements Principal {
}
