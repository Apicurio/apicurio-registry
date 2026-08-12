import { describe, expect, it, vi } from "vitest";
import { Paging } from "@models/Paging.ts";

const { fetchMock } = vi.hoisted(() => {
    const registryConfig = { artifacts: { url: "http://localhost:8080/apis/registry/v3/" } };
    (globalThis as any).ApicurioRegistryConfig = registryConfig;
    (globalThis as any).window = {
        ApicurioRegistryConfig: registryConfig,
        location: { origin: "http://localhost:8080" }
    };
    return { fetchMock: vi.fn() };
});

vi.mock("@apicurio/common-ui-components", () => ({
    useAuth: () => ({})
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
