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

import { useConfigService } from "./useConfigService";

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
