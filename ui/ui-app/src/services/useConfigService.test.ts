import { beforeEach, describe, expect, it, vi } from "vitest";

const { getRegistryConfig } = vi.hoisted(() => {
    const registryConfig: any = {
        artifacts: { url: "http://localhost:8080/apis/registry/v3/" },
        features: {}
    };
    (globalThis as any).ApicurioRegistryConfig = registryConfig;
    (globalThis as any).window = { ApicurioRegistryConfig: registryConfig };

    return { getRegistryConfig: () => registryConfig };
});

import { isFramed, isMastheadHiddenByUrl, useConfigService } from "./useConfigService";

const DEFAULT_MAX_CONTENT_LENGTH: number = 5242880;

describe("useConfigService featureUrlImportMaxContentLength", () => {
    const service = useConfigService();
    const config = getRegistryConfig();

    beforeEach(() => {
        config.features = {};
    });

    it("returns the default when the field is absent", () => {
        expect(service.featureUrlImportMaxContentLength()).toBe(DEFAULT_MAX_CONTENT_LENGTH);
    });

    it("returns the default when the field is undefined", () => {
        config.features.urlImportMaxContentLength = undefined;
        expect(service.featureUrlImportMaxContentLength()).toBe(DEFAULT_MAX_CONTENT_LENGTH);
    });

    it("returns the default when the field is null", () => {
        config.features.urlImportMaxContentLength = null;
        expect(service.featureUrlImportMaxContentLength()).toBe(DEFAULT_MAX_CONTENT_LENGTH);
    });

    it("returns the default when the field is 0", () => {
        config.features.urlImportMaxContentLength = 0;
        expect(service.featureUrlImportMaxContentLength()).toBe(DEFAULT_MAX_CONTENT_LENGTH);
    });

    it("returns the default when the field is negative", () => {
        config.features.urlImportMaxContentLength = -100;
        expect(service.featureUrlImportMaxContentLength()).toBe(DEFAULT_MAX_CONTENT_LENGTH);
    });

    it("returns the default when the field is NaN", () => {
        config.features.urlImportMaxContentLength = NaN;
        expect(service.featureUrlImportMaxContentLength()).toBe(DEFAULT_MAX_CONTENT_LENGTH);
    });

    it("returns the default when the field is Infinity", () => {
        config.features.urlImportMaxContentLength = Infinity;
        expect(service.featureUrlImportMaxContentLength()).toBe(DEFAULT_MAX_CONTENT_LENGTH);
    });

    it("returns the default when the field is a non-numeric string", () => {
        config.features.urlImportMaxContentLength = "not-a-number" as unknown as number;
        expect(service.featureUrlImportMaxContentLength()).toBe(DEFAULT_MAX_CONTENT_LENGTH);
    });

    it("returns the configured value when the field is a valid positive number", () => {
        config.features.urlImportMaxContentLength = 10485760;
        expect(service.featureUrlImportMaxContentLength()).toBe(10485760);
    });
});

describe("useConfigService masthead query parameter", () => {
    it("hides the masthead for masthead=false only when framed", () => {
        expect(isMastheadHiddenByUrl("?masthead=false", true)).toBe(true);
        expect(isMastheadHiddenByUrl("?foo=bar&masthead=false", true)).toBe(true);
        expect(isMastheadHiddenByUrl("?masthead=false", false)).toBe(false);
    });

    it("ignores other values and a missing query string", () => {
        expect(isMastheadHiddenByUrl("?masthead=true", true)).toBe(false);
        expect(isMastheadHiddenByUrl("", true)).toBe(false);
        expect(isMastheadHiddenByUrl(undefined, true)).toBe(false);
    });

    it("detects framing, treating cross-origin access errors as framed", () => {
        const top: any = {};
        expect(isFramed({ self: top, top } as any)).toBe(false);
        expect(isFramed({ self: {}, top } as any)).toBe(true);
        const crossOrigin: any = { self: {} };
        Object.defineProperty(crossOrigin, "top", { get: () => { throw new Error("SecurityError"); } });
        expect(isFramed(crossOrigin)).toBe(true);
        expect(isFramed(undefined)).toBe(false);
    });

    const loadWith = async (win: any) => {
        vi.resetModules();
        vi.stubGlobal("location", { search: "?masthead=false" });
        vi.stubGlobal("window", { ...win, ApicurioRegistryConfig: getRegistryConfig() });
        try {
            const module = await import("./useConfigService");
            return module.useConfigService().features().showMasthead;
        } finally {
            vi.unstubAllGlobals();
            vi.resetModules();
        }
    };

    it("hides the masthead when loaded framed with masthead=false", async () => {
        expect(await loadWith({ self: {}, top: {} })).toBe(false);
    });

    it("keeps the masthead when loaded unframed with masthead=false", async () => {
        const top: any = {};
        expect(await loadWith({ self: top, top })).toBeUndefined();
    });

    it("leaves showMasthead unset when the page was loaded without the parameter", () => {
        expect(useConfigService().features().showMasthead).toBeUndefined();
    });
});
