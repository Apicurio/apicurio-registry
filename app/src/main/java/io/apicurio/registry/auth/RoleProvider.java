package io.apicurio.registry.auth;

public interface RoleProvider {

    boolean isReadOnly();

    boolean isDeveloper();

    boolean isAdmin();

}
