import { describe, expect, it, vi } from "vitest";
import { Paging } from "@models/Paging.ts";

const { fetchMock, getRegistryClientMock } = vi.hoisted(() => {
    const registryConfig = { artifacts: { url: "http://localhost:8080/apis/registry/v3/" } };
    (globalThis as any).ApicurioRegistryConfig = registryConfig;
    (globalThis as any).window = {
        ApicurioRegistryConfig: registryConfig,
        location: { origin: "http://localhost:8080" }
    };
    return { fetchMock: vi.fn(), getRegistryClientMock: vi.fn() };
});

vi.mock("@apitomy/common-ui-components", () => ({
    useAuth: () => ({
        isOidcAuthEnabled: () => false,
        isBasicAuthEnabled: () => false,
        getToken: async () => undefined
    })
}));

vi.mock("@utils/rest.utils.ts", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@utils/rest.utils.ts")>()),
    getRegistryClient: getRegistryClientMock
}));

(globalThis as any).fetch = fetchMock;

import { useAgentService } from "./useAgentService";

describe("useAgentService array and string filters", () => {
    it("handles array values for capability and skill filters", async () => {
        fetchMock.mockResolvedValue({
            ok: true,
            json: async () => ({ agents: [], count: 0 })
        });

        const service = useAgentService();
        const paging: Paging = { page: 1, pageSize: 10 };

        await service.searchAgents(
            {
                name: "test-agent",
                capability: ["streaming", "pushNotifications"],
                skill: ["search", "analysis"]
            },
            paging
        );

        expect(fetchMock).toHaveBeenCalledTimes(1);
        const calledUrl = fetchMock.mock.calls[0][0] as string;
        const parsedUrl = new URL(calledUrl);

        expect(parsedUrl.searchParams.getAll("capability")).toEqual(["streaming", "pushNotifications"]);
        expect(parsedUrl.searchParams.getAll("skill")).toEqual(["search", "analysis"]);
        expect(parsedUrl.searchParams.get("name")).toBe("test-agent");
    });

    it("handles single string values for capability and skill filters", async () => {
        fetchMock.mockReset();
        fetchMock.mockResolvedValue({
            ok: true,
            json: async () => ({ agents: [], count: 0 })
        });

        const service = useAgentService();
        const paging: Paging = { page: 1, pageSize: 10 };

        await service.searchAgents(
            {
                name: "test-agent",
                capability: "streaming",
                skill: "search"
            },
            paging
        );

        expect(fetchMock).toHaveBeenCalledTimes(1);
        const calledUrl = fetchMock.mock.calls[0][0] as string;
        const parsedUrl = new URL(calledUrl);

        expect(parsedUrl.searchParams.getAll("capability")).toEqual(["streaming"]);
        expect(parsedUrl.searchParams.getAll("skill")).toEqual(["search"]);
    });

    it("handles empty arrays and arrays containing empty strings", async () => {
        fetchMock.mockReset();
        fetchMock.mockResolvedValue({
            ok: true,
            json: async () => ({ agents: [], count: 0 })
        });

        const service = useAgentService();
        const paging: Paging = { page: 1, pageSize: 10 };

        await service.searchAgents(
            {
                capability: [],
                skill: ["streaming", "", "pushNotifications"]
            },
            paging
        );

        expect(fetchMock).toHaveBeenCalledTimes(1);
        const calledUrl = fetchMock.mock.calls[0][0] as string;
        const parsedUrl = new URL(calledUrl);

        expect(parsedUrl.searchParams.getAll("capability")).toEqual([]);
        expect(parsedUrl.searchParams.getAll("skill")).toEqual(["streaming", "pushNotifications"]);
    });
});

describe("useAgentService federated search", () => {
    it("asks every registry for up to the given number of agents, with the filters as lists", async () => {
        const get = vi.fn().mockResolvedValue({ agents: [], sources: [] });
        getRegistryClientMock.mockReturnValue({ search: { federated: { get } } });

        await useAgentService().searchFederatedAgents(
            { name: "refund", capability: "streaming", skill: ["pay", "", "refund"] }, 12);

        expect(get).toHaveBeenCalledWith({
            queryParameters: {
                name: "refund",
                capability: ["streaming"],
                skill: ["pay", "refund"],
                perSourceLimit: 12
            }
        });
    });

    it("leaves out the filters that are not set", async () => {
        const get = vi.fn().mockResolvedValue({ agents: [], sources: [] });
        getRegistryClientMock.mockReturnValue({ search: { federated: { get } } });

        await useAgentService().searchFederatedAgents({ name: "" }, 5);

        expect(get).toHaveBeenCalledWith({
            queryParameters: { name: undefined, capability: undefined, skill: undefined, perSourceLimit: 5 }
        });
    });
});
