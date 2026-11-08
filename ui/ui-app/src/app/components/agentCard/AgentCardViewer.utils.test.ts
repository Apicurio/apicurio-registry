import { describe, expect, it } from "vitest";
import { hasProviderContent, providerLinkText } from "./AgentCardViewer.utils";

describe("hasProviderContent", () => {
    it("is false for an undefined provider", () => {
        expect(hasProviderContent(undefined)).toBe(false);
    });

    it("is false for an empty provider object", () => {
        expect(hasProviderContent({})).toBe(false);
    });

    it("is true when only the organization is set", () => {
        expect(hasProviderContent({ organization: "IBM" })).toBe(true);
    });

    it("is true when only the url is set", () => {
        expect(hasProviderContent({ url: "https://ibm.com" })).toBe(true);
    });

    it("is true when both are set", () => {
        expect(hasProviderContent({ organization: "IBM", url: "https://ibm.com" })).toBe(true);
    });

    it("is false when both fields are empty strings", () => {
        expect(hasProviderContent({ organization: "", url: "" })).toBe(false);
    });
});

describe("providerLinkText", () => {
    it("prefers the organization name when present", () => {
        expect(providerLinkText({ organization: "IBM", url: "https://ibm.com" })).toBe("IBM");
    });

    it("falls back to the url when the organization is absent", () => {
        expect(providerLinkText({ url: "https://ibm.com" })).toBe("https://ibm.com");
    });

    it("returns the organization when only the organization is set", () => {
        expect(providerLinkText({ organization: "IBM" })).toBe("IBM");
    });
});
