package io.apicurio.registry.storage.error;

import lombok.Getter;

public class PeerAlreadyExistsException extends AlreadyExistsException {

    private static final long serialVersionUID = 1L;

    @Getter
    private String peerId;

    public PeerAlreadyExistsException(String peerId) {
        super("A peer registry with id '" + peerId + "' already exists.");
        this.peerId = peerId;
    }
}
