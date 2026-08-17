import { describe, expect, it, vi } from "vitest";

vi.hoisted(() => {
    const registryConfig = { artifacts: { url: "http://localhost:8080/apis/registry/v3/" } };
    (globalThis as any).ApicurioRegistryConfig = registryConfig;
    (globalThis as any).window = { ApicurioRegistryConfig: registryConfig };
});

vi.mock("@apitomy/common-ui-components", () => ({
    useAuth: () => ({})
}));

import { getBaseUrl } from "./useMcpToolsService";
import { ConfigService } from "./useConfigService";

describe("useMcpToolsService getBaseUrl", () => {
    it("strips /apis/registry/v3 suffix correctly", () => {
        const mockConfig = {
            artifactsUrl: () => "http://localhost:8080/apis/registry/v3"
        } as ConfigService;

        expect(getBaseUrl(mockConfig)).toBe("http://localhost:8080");
    });

    it("handles trailing slash before suffix removal", () => {
        const mockConfig = {
            artifactsUrl: () => "http://localhost:8080/apis/registry/v3/"
        } as ConfigService;

        expect(getBaseUrl(mockConfig)).toBe("http://localhost:8080");
    });

    it("falls back to origin for non-standard path", () => {
        const mockConfig = {
            artifactsUrl: () => "http://myregistry.example.com:9090/custom/api/path"
        } as ConfigService;

        expect(getBaseUrl(mockConfig)).toBe("http://myregistry.example.com:9090");
    });
});
