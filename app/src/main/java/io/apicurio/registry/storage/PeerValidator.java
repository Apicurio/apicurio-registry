package io.apicurio.registry.storage;

import io.apicurio.registry.storage.dto.PeerDto;
import io.apicurio.registry.storage.error.InvalidPeerException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates a peer registry definition before it is persisted. Covers structural invariants
 * only; scheme/address policy belongs to #8574 and #8575.
 */
public final class PeerValidator {

    private static final Pattern PEER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,256}$");
    private static final Pattern CREDENTIAL_SECRET_REF_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,253}$");

    public static final String RESERVED_LOCAL_PEER_ID = "local";

    private static final int MAX_URL_LENGTH = 1024;
    private static final int MAX_NAME_LENGTH = 512;
    private static final int MAX_DESCRIPTION_LENGTH = 1024;

    private PeerValidator() {
    }

    public static void validate(PeerDto peer) {
        if (peer == null) {
            throw new InvalidPeerException("Peer is required.");
        }
        validatePeerId(peer.getPeerId());
        validateUrl(peer.getUrl());
        validateCredentialSecretRef(peer.getCredentialSecretRef());
        validateName(peer.getName());
        validateDescription(peer.getDescription());
    }

    private static void validatePeerId(String peerId) {
        if (peerId == null || peerId.isBlank()) {
            throw new InvalidPeerException("Peer id is required.");
        }
        if (!PEER_ID_PATTERN.matcher(peerId).matches()) {
            throw new InvalidPeerException("Peer id is invalid.");
        }
        if (".".equals(peerId) || "..".equals(peerId)) {
            throw new InvalidPeerException("Peer id is invalid.");
        }
        if (RESERVED_LOCAL_PEER_ID.equals(peerId.toLowerCase(Locale.ROOT))) {
            throw new InvalidPeerException("Peer id '" + RESERVED_LOCAL_PEER_ID + "' is reserved.");
        }
    }

    private static void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidPeerException("Peer url is required.");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new InvalidPeerException("Peer url exceeds the maximum length of " + MAX_URL_LENGTH + ".");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException ex) {
            throw new InvalidPeerException("Peer url is not a valid URI.");
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw new InvalidPeerException("Peer url must be an absolute URI with a host.");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidPeerException("Peer url must not contain userinfo.");
        }
        if (uri.getFragment() != null) {
            throw new InvalidPeerException("Peer url must not contain a fragment.");
        }
    }

    private static void validateCredentialSecretRef(String credentialSecretRef) {
        if (credentialSecretRef == null) {
            return;
        }
        if (!CREDENTIAL_SECRET_REF_PATTERN.matcher(credentialSecretRef).matches()) {
            throw new InvalidPeerException("Peer credential secret reference is invalid.");
        }
        if (".".equals(credentialSecretRef) || "..".equals(credentialSecretRef)) {
            throw new InvalidPeerException("Peer credential secret reference is invalid.");
        }
    }

    private static void validateName(String name) {
        if (name != null && name.length() > MAX_NAME_LENGTH) {
            throw new InvalidPeerException("Peer name exceeds the maximum length of " + MAX_NAME_LENGTH + ".");
        }
    }

    private static void validateDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidPeerException(
                    "Peer description exceeds the maximum length of " + MAX_DESCRIPTION_LENGTH + ".");
        }
    }
}
