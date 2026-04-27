package io.apicurio.authz;

import io.kroxylicious.proxy.authentication.Principal;

public record RolePrincipal(String name) implements Principal {
}
