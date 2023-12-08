package io.apicurio.registry.storage.error;

import lombok.Getter;

public class CommentNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -3708928902316703363L;

    @Getter
    private String commentId;


    public CommentNotFoundException(String commentId) {
        super("No comment with ID '" + commentId + "' was found.");
        this.commentId = commentId;
    }
}
