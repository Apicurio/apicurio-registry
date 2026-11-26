import { describe, expect, it, vi } from "vitest";
import { Paging } from "@models/Paging.ts";

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

import { useAdminService } from "./useAdminService";

// Regression test for issue #9086: the `limit` query param must always equal
// `pageSize`, regardless of which page is being requested. Previously it was
// computed as `offset + pageSize`, so it grew on every page past page 1.
describe("useAdminService pagination", () => {
    it("sends a constant limit and correct offset across pages for getRoleMappings", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, roleMappings: [] });
        getRegistryClientMock.mockReturnValue({ admin: { roleMappings: { get } } });

        const service = useAdminService();
        const pagesToCheck: { page: number; expectedOffset: number }[] = [
            { page: 1, expectedOffset: 0 },
            { page: 2, expectedOffset: 10 },
            { page: 3, expectedOffset: 20 }
        ];
        for (const { page } of pagesToCheck) {
            const paging: Paging = { page, pageSize: 10 };
            await service.getRoleMappings(paging);
        }

        expect(get).toHaveBeenCalledTimes(pagesToCheck.length);
        const queryParams = get.mock.calls.map(call => call[0].queryParameters);
        queryParams.forEach((params: any, i: number) => {
            expect(params.limit).toBe(10);
            expect(params.offset).toBe(pagesToCheck[i].expectedOffset);
        });
    });

    it("sends a constant limit and correct offset across pages for getPeers", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, peers: [] });
        getRegistryClientMock.mockReturnValue({ admin: { peers: { get } } });

        const service = useAdminService();
        await service.getPeers({ page: 1, pageSize: 25 });
        await service.getPeers({ page: 3, pageSize: 25 });

        expect(get).toHaveBeenCalledTimes(2);
        expect(get.mock.calls[0][0].queryParameters).toEqual({ limit: 25, offset: 0 });
        expect(get.mock.calls[1][0].queryParameters).toEqual({ limit: 25, offset: 50 });
    });
});

describe("useAdminService peers", () => {
    it("returns the peers the server listed", async () => {
        const results = { count: 1, peers: [{ peerId: "eu", url: "https://eu.example.com", enabled: true }] };
        getRegistryClientMock.mockReturnValue({ admin: { peers: { get: vi.fn().mockResolvedValue(results) } } });

        expect(await useAdminService().getPeers({ page: 1, pageSize: 100 })).toBe(results);
    });

    it("posts a new peer to the collection", async () => {
        const post = vi.fn().mockResolvedValue(undefined);
        getRegistryClientMock.mockReturnValue({ admin: { peers: { post } } });
        const peer = { peerId: "eu", url: "https://eu.example.com", enabled: true };

        await useAdminService().createPeer(peer);

        expect(post).toHaveBeenCalledTimes(1);
        expect(post).toHaveBeenCalledWith(peer);
    });

    it("puts an update to the peer with the given id", async () => {
        const put = vi.fn().mockResolvedValue(undefined);
        const byPeerId = vi.fn().mockReturnValue({ put });
        getRegistryClientMock.mockReturnValue({ admin: { peers: { byPeerId } } });
        const update = { url: "https://eu2.example.com", enabled: false };

        await useAdminService().updatePeer("eu", update);

        expect(byPeerId).toHaveBeenCalledWith("eu");
        expect(put).toHaveBeenCalledWith(update);
    });

    it("deletes the peer with the given id", async () => {
        const del = vi.fn().mockResolvedValue(undefined);
        const byPeerId = vi.fn().mockReturnValue({ delete: del });
        getRegistryClientMock.mockReturnValue({ admin: { peers: { byPeerId } } });

        await useAdminService().deletePeer("eu");

        expect(byPeerId).toHaveBeenCalledWith("eu");
        expect(del).toHaveBeenCalledTimes(1);
    });

    it("passes the server's error on to the caller", async () => {
        const error = { responseStatusCode: 409, detail: "Federation is not enabled on this registry instance." };
        getRegistryClientMock.mockReturnValue({ admin: { peers: { get: vi.fn().mockRejectedValue(error) } } });

        await expect(useAdminService().getPeers({ page: 1, pageSize: 100 })).rejects.toBe(error);
    });
});
