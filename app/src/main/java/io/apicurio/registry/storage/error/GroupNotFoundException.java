package io.apicurio.registry.storage.error;

import lombok.Getter;

public class GroupNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -5024749463194169679L;

    @Getter
    private final String groupId;


    public GroupNotFoundException(String groupId) {
        super(message(groupId));
        this.groupId = groupId;
    }


    public GroupNotFoundException(String groupId, Throwable cause) {
        super(message(groupId), cause);
        this.groupId = groupId;
    }


    private static String message(String groupId) {
        return "No group '" + groupId + "' was found.";
    }
}
