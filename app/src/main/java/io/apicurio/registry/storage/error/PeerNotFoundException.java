package io.apicurio.registry.storage.error;

import lombok.Getter;

public class PeerNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    @Getter
    private String peerId;

    public PeerNotFoundException(String peerId) {
        super("No peer registry with id '" + peerId + "' was found.");
        this.peerId = peerId;
    }
}
