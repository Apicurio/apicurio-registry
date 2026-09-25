import { beforeEach, describe, expect, it, vi } from "vitest";

const { getArtifactTypes } = vi.hoisted(() => ({ getArtifactTypes: vi.fn() }));

// The service is a hook; outside React, useMemo/useRef just need to hand back their values.
vi.mock("react", () => ({
    useMemo: (factory: () => unknown) => factory(),
    useRef: (value: unknown) => ({ current: value })
}));

vi.mock("@services/useAdminService.ts", () => ({
    useAdminService: () => ({ getArtifactTypes })
}));

const CORE_TYPES = ["PROTOBUF", "OPENAPI", "ASYNCAPI", "JSON", "AVRO"];
const AGENT_TYPES = ["AGENT_CARD", "MCP_TOOL", "MODEL_SCHEMA", "PROMPT_TEMPLATE"];

// The type list is cached at module level, so each test needs a fresh module instance.
const loadService = async () => {
    vi.resetModules();
    const module = await import("./useArtifactTypesService");
    return { ArtifactTypes: module.ArtifactTypes, service: module.useArtifactTypesService() };
};

describe("ArtifactTypes.isAgentType", () => {
    it("recognises exactly the four agent registry types", async () => {
        const { ArtifactTypes } = await loadService();
        expect(AGENT_TYPES.map(t => ArtifactTypes.isAgentType(t))).toEqual([true, true, true, true]);
        expect(CORE_TYPES.map(t => ArtifactTypes.isAgentType(t))).toEqual([false, false, false, false, false]);
        expect(ArtifactTypes.isAgentType(undefined)).toBe(false);
        expect(ArtifactTypes.isAgentType(null)).toBe(false);
    });
});

describe("useArtifactTypesService.agentTypesSupported", () => {
    beforeEach(() => {
        getArtifactTypes.mockReset();
    });

    it("is true when the server registers the agent types", async () => {
        getArtifactTypes.mockResolvedValue([...CORE_TYPES, ...AGENT_TYPES].map(name => ({ name })));
        const { service } = await loadService();

        await expect(service.agentTypesSupported()).resolves.toBe(true);
    });

    it("is false when the server was built without agents", async () => {
        getArtifactTypes.mockResolvedValue(CORE_TYPES.map(name => ({ name })));
        const { service } = await loadService();

        await expect(service.agentTypesSupported()).resolves.toBe(false);
        await expect(service.allTypes()).resolves.toEqual(CORE_TYPES);
    });

    it("falls back to the built-in type list, including agent types, when the server call fails", async () => {
        getArtifactTypes.mockRejectedValue(new Error("network down"));
        const { service } = await loadService();

        await expect(service.agentTypesSupported()).resolves.toBe(true);
        expect(getArtifactTypes).toHaveBeenCalledTimes(1);
    });
});
