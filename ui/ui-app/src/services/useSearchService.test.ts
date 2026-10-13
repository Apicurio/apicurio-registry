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

import { FilterBy, toPartialNameFilter, useSearchService } from "./useSearchService";

describe("toPartialNameFilter", () => {
    it("returns empty string for empty or whitespace input", () => {
        expect(toPartialNameFilter("")).toBe("");
        expect(toPartialNameFilter("   ")).toBe("");
        expect(toPartialNameFilter(undefined)).toBe("");
    });

    it("wraps fragment in wildcards for substring matching", () => {
        expect(toPartialNameFilter("Cart")).toBe("*Cart*");
        expect(toPartialNameFilter("  Cart  ")).toBe("*Cart*");
    });

    it("preserves explicit user-provided wildcards", () => {
        expect(toPartialNameFilter("Cart*")).toBe("Cart*");
        expect(toPartialNameFilter("*Cart")).toBe("*Cart");
        expect(toPartialNameFilter("*Cart*")).toBe("*Cart*");
        expect(toPartialNameFilter("*")).toBe("*");
    });
});

// Regression test for issue #9086: the `limit` query param must always equal
// `pageSize`, regardless of which page is being requested. Previously it was
// computed as `offset + pageSize`, so it grew on every page past page 1.
describe("useSearchService pagination", () => {
    const pagesToCheck: { page: number; expectedOffset: number }[] = [
        { page: 1, expectedOffset: 0 },
        { page: 2, expectedOffset: 10 },
        { page: 3, expectedOffset: 20 }
    ];

    it("sends a constant limit and correct offset across pages for searchGroups", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, groups: [] });
        getRegistryClientMock.mockReturnValue({ search: { groups: { get } } });

        const service = useSearchService();
        for (const { page } of pagesToCheck) {
            const paging: Paging = { page, pageSize: 10 };
            await service.searchGroups([], "groupId" as any, "asc" as any, paging);
        }

        expect(get).toHaveBeenCalledTimes(pagesToCheck.length);
        const queryParams = get.mock.calls.map(call => call[0].queryParameters);
        queryParams.forEach((params: any, i: number) => {
            expect(params.limit).toBe(10);
            expect(params.offset).toBe(pagesToCheck[i].expectedOffset);
        });
    });

    it("sends a constant limit and correct offset across pages for searchArtifacts", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, artifacts: [] });
        getRegistryClientMock.mockReturnValue({ search: { artifacts: { get } } });

        const service = useSearchService();
        for (const { page } of pagesToCheck) {
            const paging: Paging = { page, pageSize: 10 };
            await service.searchArtifacts(
                [{ by: FilterBy.name, value: "" }], "name" as any, "asc" as any, paging
            );
        }

        expect(get).toHaveBeenCalledTimes(pagesToCheck.length);
        const queryParams = get.mock.calls.map(call => call[0].queryParameters);
        queryParams.forEach((params: any, i: number) => {
            expect(params.limit).toBe(10);
            expect(params.offset).toBe(pagesToCheck[i].expectedOffset);
        });
    });

    it("sends a constant limit and correct offset across pages for searchVersions", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, versions: [] });
        getRegistryClientMock.mockReturnValue({ search: { versions: { get } } });

        const service = useSearchService();
        for (const { page } of pagesToCheck) {
            const paging: Paging = { page, pageSize: 10 };
            await service.searchVersions([], "version" as any, "asc" as any, paging);
        }

        expect(get).toHaveBeenCalledTimes(pagesToCheck.length);
        const queryParams = get.mock.calls.map(call => call[0].queryParameters);
        queryParams.forEach((params: any, i: number) => {
            expect(params.limit).toBe(10);
            expect(params.offset).toBe(pagesToCheck[i].expectedOffset);
        });
    });
});

// Regression test for issue #10128: name filter must wrap input in wildcards
// so partial names return matching artifacts/versions in console search.
describe("useSearchService name filter wildcard wrapping (#10128)", () => {
    it("wraps partial name in wildcards for searchArtifacts", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, artifacts: [] });
        getRegistryClientMock.mockReturnValue({ search: { artifacts: { get } } });

        const service = useSearchService();
        await service.searchArtifacts(
            [{ by: FilterBy.name, value: "Cart" }], "name" as any, "asc" as any, { page: 1, pageSize: 10 }
        );

        expect(get).toHaveBeenCalledTimes(1);
        expect(get.mock.calls[0][0].queryParameters.name).toBe("*Cart*");
    });

    it("preserves explicit wildcards in searchArtifacts", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, artifacts: [] });
        getRegistryClientMock.mockReturnValue({ search: { artifacts: { get } } });

        const service = useSearchService();
        await service.searchArtifacts(
            [{ by: FilterBy.name, value: "Cart*" }], "name" as any, "asc" as any, { page: 1, pageSize: 10 }
        );

        expect(get).toHaveBeenCalledTimes(1);
        expect(get.mock.calls[0][0].queryParameters.name).toBe("Cart*");
    });

    it("wraps partial name in wildcards for searchVersions", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, versions: [] });
        getRegistryClientMock.mockReturnValue({ search: { versions: { get } } });

        const service = useSearchService();
        await service.searchVersions(
            [{ by: FilterBy.name, value: "Cart" }], "name" as any, "asc" as any, { page: 1, pageSize: 10 }
        );

        expect(get).toHaveBeenCalledTimes(1);
        expect(get.mock.calls[0][0].queryParameters.name).toBe("*Cart*");
    });

    it("preserves explicit wildcards in searchVersions", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, versions: [] });
        getRegistryClientMock.mockReturnValue({ search: { versions: { get } } });

        const service = useSearchService();
        await service.searchVersions(
            [{ by: FilterBy.name, value: "*Cart" }], "name" as any, "asc" as any, { page: 1, pageSize: 10 }
        );

        expect(get).toHaveBeenCalledTimes(1);
        expect(get.mock.calls[0][0].queryParameters.name).toBe("*Cart");
    });
});
