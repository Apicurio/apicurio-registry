/**
 * Client-side checks for the peer registry form. They mirror the structural rules of the server's
 * PeerValidator so that obvious mistakes are caught before a request is sent. The server stays
 * authoritative: it also applies the scheme and address policy (https only, no loopback or
 * link-local addresses unless an administrator opted in), which the UI cannot know.
 */

export const RESERVED_PEER_ID = "local";
export const MAX_PEER_ID_LENGTH = 256;
export const MAX_URL_LENGTH = 1024;
export const MAX_NAME_LENGTH = 512;
export const MAX_DESCRIPTION_LENGTH = 1024;

const PEER_ID_PATTERN = /^[a-z0-9._-]{1,256}$/;
const CREDENTIAL_SECRET_REF_PATTERN = /^[a-zA-Z0-9._-]{1,253}$/;

export const validatePeerId = (peerId: string): string | undefined => {
    if (!peerId.trim()) {
        return "A peer ID is required.";
    }
    if (!PEER_ID_PATTERN.test(peerId) || peerId === "." || peerId === "..") {
        return `The peer ID can only contain lowercase letters, digits, '.', '_' and '-' (at most ${MAX_PEER_ID_LENGTH} characters).`;
    }
    if (peerId === RESERVED_PEER_ID) {
        return `The peer ID '${RESERVED_PEER_ID}' is reserved for this registry.`;
    }
    return undefined;
};

export const validatePeerUrl = (url: string): string | undefined => {
    if (!url.trim()) {
        return "A URL is required.";
    }
    if (url.length > MAX_URL_LENGTH) {
        return `The URL can be at most ${MAX_URL_LENGTH} characters long.`;
    }
    let parsed: URL;
    try {
        parsed = new URL(url);
    } catch {
        return "Enter an absolute URL, for example https://registry.example.com.";
    }
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") {
        return "The URL must start with https:// (or http:// if your administrator allows it).";
    }
    // The browser accepts port 0 (and rejects ports above 65535 when parsing), the server accepts 1 to 65535.
    if (parsed.port === "0") {
        return "The URL port must be between 1 and 65535.";
    }
    if (parsed.username || parsed.password) {
        return "The URL must not contain a user name or password.";
    }
    if (parsed.hash || url.includes("#")) {
        return "The URL must not contain a fragment (#).";
    }
    if (parsed.search || url.includes("?")) {
        return "The URL must not contain a query string (?).";
    }
    return undefined;
};

export const validateCredentialSecretRef = (ref: string): string | undefined => {
    if (!ref) {
        return undefined;
    }
    if (!CREDENTIAL_SECRET_REF_PATTERN.test(ref) || ref === "." || ref.startsWith("..")) {
        return "A credential reference can only contain letters, digits, '.', '_' and '-', and cannot start with '..'.";
    }
    return undefined;
};

export const validatePeerName = (name: string): string | undefined => {
    if (name.length > MAX_NAME_LENGTH) {
        return `The name can be at most ${MAX_NAME_LENGTH} characters long.`;
    }
    return undefined;
};

export const validatePeerDescription = (description: string): string | undefined => {
    if (description.length > MAX_DESCRIPTION_LENGTH) {
        return `The description can be at most ${MAX_DESCRIPTION_LENGTH} characters long.`;
    }
    return undefined;
};

/**
 * Returns the message the server sent with an error (its problem details) when there is one.
 * The server prefixes the detail with the name of its exception class, which means nothing to a
 * user, so that prefix is dropped.
 */
export const peerErrorMessage = (error: any, fallback: string): string => {
    const message: string = error?.detail || error?.title || error?.message || fallback;
    return message.replace(/^\w+Exception:\s*/, "");
};
