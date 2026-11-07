import { describe, expect, it } from "vitest";
import {
    MAX_DESCRIPTION_LENGTH,
    MAX_NAME_LENGTH,
    MAX_URL_LENGTH,
    peerErrorMessage,
    validateCredentialSecretRef,
    validatePeerDescription,
    validatePeerId,
    validatePeerName,
    validatePeerUrl
} from "./peerValidation";

describe("validatePeerId", () => {
    it("accepts lowercase letters, digits, dots, underscores and hyphens", () => {
        expect(validatePeerId("eu-west_1.registry")).toBeUndefined();
    });

    it("requires a value", () => {
        expect(validatePeerId("  ")).toBe("A peer ID is required.");
    });

    it("rejects uppercase letters, spaces and slashes", () => {
        for (const id of ["EU", "eu west", "eu/west"]) {
            expect(validatePeerId(id)).toContain("lowercase letters, digits");
        }
    });

    it("rejects the dot-only ids and ids longer than 256 characters", () => {
        expect(validatePeerId(".")).toContain("lowercase letters, digits");
        expect(validatePeerId("..")).toContain("lowercase letters, digits");
        expect(validatePeerId("a".repeat(256))).toBeUndefined();
        expect(validatePeerId("a".repeat(257))).toContain("at most 256 characters");
    });

    it("rejects the reserved id local", () => {
        expect(validatePeerId("local")).toBe("The peer ID 'local' is reserved for this registry.");
    });
});

describe("validatePeerUrl", () => {
    it("accepts https and http urls, with a port and a path", () => {
        expect(validatePeerUrl("https://registry.eu.example.com")).toBeUndefined();
        expect(validatePeerUrl("http://registry:8080/base")).toBeUndefined();
    });

    it("requires a value", () => {
        expect(validatePeerUrl("")).toBe("A URL is required.");
    });

    it("rejects text that is not an absolute url", () => {
        expect(validatePeerUrl("registry.example.com")).toContain("absolute URL");
    });

    it("rejects schemes other than http and https", () => {
        expect(validatePeerUrl("ftp://registry.example.com")).toContain("must start with https://");
    });

    it("rejects user info, fragments and query strings", () => {
        expect(validatePeerUrl("https://user:secret@registry.example.com")).toBe("The URL must not contain a user name or password.");
        expect(validatePeerUrl("https://registry.example.com/#top")).toBe("The URL must not contain a fragment (#).");
        expect(validatePeerUrl("https://registry.example.com/?a=1")).toBe("The URL must not contain a query string (?).");
    });

    it("rejects a port outside the TCP range", () => {
        expect(validatePeerUrl("https://registry.example.com:70000")).toContain("absolute URL");
    });

    it("rejects port 0, which the browser accepts but the server does not", () => {
        expect(validatePeerUrl("https://registry.example.com:0")).toBe("The URL port must be between 1 and 65535.");
        expect(validatePeerUrl("https://registry.example.com:1/base")).toBeUndefined();
        expect(validatePeerUrl("https://registry.example.com:65535")).toBeUndefined();
    });

    it("rejects urls longer than the limit", () => {
        const url = "https://registry.example.com/" + "a".repeat(MAX_URL_LENGTH);
        expect(validatePeerUrl(url)).toBe(`The URL can be at most ${MAX_URL_LENGTH} characters long.`);
    });
});

describe("validateCredentialSecretRef", () => {
    it("treats the reference as optional", () => {
        expect(validateCredentialSecretRef("")).toBeUndefined();
    });

    it("accepts letters of either case, digits, dots, underscores and hyphens", () => {
        expect(validateCredentialSecretRef("Peer_EU-1.token")).toBeUndefined();
    });

    it("rejects path separators, spaces and names starting with two dots", () => {
        for (const ref of ["a/b", "a b", "..data", ".", "../secret"]) {
            expect(validateCredentialSecretRef(ref)).toContain("can only contain");
        }
    });
});

describe("validatePeerName and validatePeerDescription", () => {
    it("accept values up to the limit and reject longer ones", () => {
        expect(validatePeerName("n".repeat(MAX_NAME_LENGTH))).toBeUndefined();
        expect(validatePeerName("n".repeat(MAX_NAME_LENGTH + 1))).toBe(`The name can be at most ${MAX_NAME_LENGTH} characters long.`);
        expect(validatePeerDescription("d".repeat(MAX_DESCRIPTION_LENGTH))).toBeUndefined();
        expect(validatePeerDescription("d".repeat(MAX_DESCRIPTION_LENGTH + 1)))
            .toBe(`The description can be at most ${MAX_DESCRIPTION_LENGTH} characters long.`);
    });
});

describe("peerErrorMessage", () => {
    it("prefers the problem detail, then the title, then the message, then the fallback", () => {
        expect(peerErrorMessage({ detail: "A peer with this id exists.", title: "Conflict" }, "fallback")).toBe("A peer with this id exists.");
        expect(peerErrorMessage({ title: "Conflict", message: "m" }, "fallback")).toBe("Conflict");
        expect(peerErrorMessage({ message: "m" }, "fallback")).toBe("m");
        expect(peerErrorMessage({}, "fallback")).toBe("fallback");
        expect(peerErrorMessage(undefined, "fallback")).toBe("fallback");
    });

    it("drops the exception class name the server puts in front of the detail", () => {
        expect(peerErrorMessage({ detail: "PeerAlreadyExistsException: A peer registry with id 'eu-west' already exists." }, "fallback"))
            .toBe("A peer registry with id 'eu-west' already exists.");
        expect(peerErrorMessage({ detail: "ConflictException: Federation is not enabled on this registry instance." }, "fallback"))
            .toBe("Federation is not enabled on this registry instance.");
    });

    it("keeps a message that only mentions an exception further in", () => {
        expect(peerErrorMessage({ detail: "The request failed with a ConflictException: try again." }, "fallback"))
            .toBe("The request failed with a ConflictException: try again.");
    });
});
