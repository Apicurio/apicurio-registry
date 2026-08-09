import { describe, expect, it, vi, beforeEach } from "vitest";

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

describe("useConfigService uiImportMaxContentLength", () => {
    const service = useConfigService();
    const config = getRegistryConfig();

    beforeEach(() => {
        config.features = {};
    });

    it("returns default (5242880) when field is absent", () => {
        expect(service.featureUrlImportMaxContentLength()).toBe(5242880);
    });

    it("returns default (5242880) when field is undefined", () => {
        config.features.urlImportMaxContentLength = undefined;
        expect(service.featureUrlImportMaxContentLength()).toBe(5242880);
    });

    it("returns default (5242880) when field is null", () => {
        config.features.urlImportMaxContentLength = null;
        expect(service.featureUrlImportMaxContentLength()).toBe(5242880);
    });

    it("returns default (5242880) when field is 0", () => {
        config.features.urlImportMaxContentLength = 0;
        expect(service.featureUrlImportMaxContentLength()).toBe(5242880);
    });

    it("returns default (5242880) when field is negative", () => {
        config.features.urlImportMaxContentLength = -100;
        expect(service.featureUrlImportMaxContentLength()).toBe(5242880);
    });

    it("returns default (5242880) when field is NaN", () => {
        config.features.urlImportMaxContentLength = NaN;
        expect(service.featureUrlImportMaxContentLength()).toBe(5242880);
    });

    it("returns default (5242880) when field is non-numeric string", () => {
        config.features.urlImportMaxContentLength = "invalid";
        expect(service.featureUrlImportMaxContentLength()).toBe(5242880);
    });

    it("returns configured value when field is a valid positive number", () => {
        config.features.urlImportMaxContentLength = 10485760;
        expect(service.featureUrlImportMaxContentLength()).toBe(10485760);
    });

    it("returns coerced numeric value when field is a numeric string", () => {
        config.features.urlImportMaxContentLength = "20971520";
        expect(service.featureUrlImportMaxContentLength()).toBe(20971520);
    });
});
