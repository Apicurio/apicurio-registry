import { describe, expect, it, vi } from "vitest";

const { getRegistryClientMock } = vi.hoisted(() => {
    // useConfigService.ts reads this global at module load time (normally injected
    // by the app's config.js in a real browser); provide a minimal stand-in so the
    // service modules under test can be imported in a Vitest (node) environment.
    const registryConfig = { artifacts: { url: "http://localhost:8080/apis/registry/v3/" } };
    (globalThis as any).ApicurioRegistryConfig = registryConfig;
    (globalThis as any).window = { ApicurioRegistryConfig: registryConfig };
    return { getRegistryClientMock: vi.fn() };
});

vi.mock("@apitomy/common-ui-components", () => ({
    useAuth: () => ({})
}));

vi.mock("@utils/rest.utils.ts", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@utils/rest.utils.ts")>();
    return {
        ...actual,
        getRegistryClient: getRegistryClientMock
    };
});

import { useMcpToolService } from "./useMcpToolService";

const mockCompatibilityGet = (get: ReturnType<typeof vi.fn>) => {
    const byArtifactId = vi.fn().mockReturnValue({ compatibility: { get } });
    const byGroupId = vi.fn().mockReturnValue({ byArtifactId });
    getRegistryClientMock.mockReturnValue({ wellKnown: { mcpTools: { byGroupId } } });
    return { byGroupId, byArtifactId };
};

describe("useMcpToolService.getToolCompatibility", () => {
    it("sends the tool, version, verdicts and page to the compatibility operation", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, results: [] });
        const { byGroupId, byArtifactId } = mockCompatibilityGet(get);

        await useMcpToolService().getToolCompatibility("my-group", "my-tool", "2", ["INCOMPATIBLE", "INDETERMINATE"],
            { page: 3, pageSize: 10 });

        expect(byGroupId).toHaveBeenCalledWith("my-group");
        expect(byArtifactId).toHaveBeenCalledWith("my-tool");
        expect(get).toHaveBeenCalledWith({
            queryParameters: { version: "2", verdict: ["INCOMPATIBLE", "INDETERMINATE"], offset: 20, limit: 10 }
        });
    });

    it("leaves the verdict out when none is selected, so that every verdict is returned", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, results: [] });
        mockCompatibilityGet(get);

        await useMcpToolService().getToolCompatibility("my-group", "my-tool", "1", [], { page: 1, pageSize: 20 });

        expect(get).toHaveBeenCalledWith({
            queryParameters: { version: "1", verdict: undefined, offset: 0, limit: 20 }
        });
    });

    it("returns the response unchanged", async () => {
        const response = { candidateTotal: 3, evaluated: 2, count: 1, results: [{ verdict: "COMPATIBLE" }] };
        mockCompatibilityGet(vi.fn().mockResolvedValue(response));

        await expect(useMcpToolService().getToolCompatibility("my-group", "my-tool", "1", [], { page: 1, pageSize: 20 }))
            .resolves.toBe(response);
    });

    it("returns undefined when the API answers 404", async () => {
        mockCompatibilityGet(vi.fn().mockRejectedValue({ responseStatusCode: 404 }));

        await expect(useMcpToolService().getToolCompatibility("my-group", "my-tool", "1", [], { page: 1, pageSize: 20 }))
            .resolves.toBeUndefined();
    });

    it("propagates any other error", async () => {
        const error = { responseStatusCode: 500 };
        mockCompatibilityGet(vi.fn().mockRejectedValue(error));

        await expect(useMcpToolService().getToolCompatibility("my-group", "my-tool", "1", [], { page: 1, pageSize: 20 }))
            .rejects.toBe(error);
    });
});
