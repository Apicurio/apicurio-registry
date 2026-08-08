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

vi.mock("@apicurio/common-ui-components", () => ({
    useAuth: () => ({})
}));

vi.mock("@utils/rest.utils.ts", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@utils/rest.utils.ts")>();
    return {
        ...actual,
        getRegistryClient: getRegistryClientMock
    };
});

import { useDraftsService } from "./useDraftsService";

// Regression test for issue #9086: the `limit` query param must always equal
// `pageSize`, regardless of which page is being requested. Previously it was
// computed as `offset + pageSize`, so it grew on every page past page 1.
describe("useDraftsService pagination", () => {
    it("sends a constant limit and correct offset across pages for searchDrafts", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, versions: [] });
        getRegistryClientMock.mockReturnValue({ search: { versions: { get } } });

        const service = useDraftsService();
        const pagesToCheck: { page: number; expectedOffset: number }[] = [
            { page: 1, expectedOffset: 0 },
            { page: 2, expectedOffset: 10 },
            { page: 3, expectedOffset: 20 }
        ];
        for (const { page } of pagesToCheck) {
            const paging: Paging = { page, pageSize: 10 };
            await service.searchDrafts([], "name" as any, "asc" as any, paging);
        }

        expect(get).toHaveBeenCalledTimes(pagesToCheck.length);
        const queryParams = get.mock.calls.map(call => call[0].queryParameters);
        queryParams.forEach((params: any, i: number) => {
            expect(params.limit).toBe(10);
            expect(params.offset).toBe(pagesToCheck[i].expectedOffset);
        });
    });
});
