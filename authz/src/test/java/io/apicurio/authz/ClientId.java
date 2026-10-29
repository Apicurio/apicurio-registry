package io.apicurio.authz;

import io.kroxylicious.identity.Principal;

/** A principal type other than {@link Role}, to check that rules match the principal class exactly. */
public record ClientId(String name) implements Principal {
}
