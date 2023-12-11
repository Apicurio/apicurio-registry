package io.apicurio.registry.storage.error;

import lombok.Getter;

public class RoleMappingAlreadyExistsException extends AlreadyExistsException {

    private static final long serialVersionUID = 2950093578954587049L;

    @Getter
    private String principalId;

    @Getter
    private String role;


    public RoleMappingAlreadyExistsException(String principalId, String role) {
        super("A mapping for principal '" + principalId + "' and role '" + role + "' already exists.");
        this.principalId = principalId;
        this.role = role;
    }
}
