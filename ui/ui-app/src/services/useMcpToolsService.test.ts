import { describe, expect, it } from "vitest";
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
