package io.apicurio.registry.storage.error;

import lombok.Getter;

public class GroupAlreadyExistsException extends AlreadyExistsException {

    private static final long serialVersionUID = 2412206165461946827L;

    @Getter
    private final String groupId;

    public GroupAlreadyExistsException(String groupId) {
        super("Group '" + groupId + "' already exists.");
        this.groupId = groupId;
    }
}
