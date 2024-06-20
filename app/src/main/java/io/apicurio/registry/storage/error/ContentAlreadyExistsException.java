package io.apicurio.registry.storage.error;

import lombok.Getter;

public class ContentAlreadyExistsException extends AlreadyExistsException {

    private static final long serialVersionUID = 6415287691931770433L;

    @Getter
    private final Long contentId;

    public ContentAlreadyExistsException(long contentId) {
        super("Content with ID " + contentId + " already exists.");
        this.contentId = contentId;
    }
}
