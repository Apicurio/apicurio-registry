import { beforeEach, describe, expect, it, vi } from "vitest";
import { Paging } from "@models/Paging.ts";

const { axiosGetMock, createAuthOptionsMock, getRegistryClientMock } = vi.hoisted(() => {
    // useConfigService.ts reads this global at module load time (normally injected
    // by the app's config.js in a real browser); provide a minimal stand-in so the
    // service modules under test can be imported in a Vitest (node) environment.
    const registryConfig = { artifacts: { url: "http://localhost:8080/apis/registry/v3/" } };
    (globalThis as any).ApicurioRegistryConfig = registryConfig;
    (globalThis as any).window = { ApicurioRegistryConfig: registryConfig };
    return {
        axiosGetMock: vi.fn(),
        createAuthOptionsMock: vi.fn(),
        getRegistryClientMock: vi.fn()
    };
});

vi.mock("@apicurio/common-ui-components", () => ({
    useAuth: () => ({})
}));

vi.mock("@utils/rest.utils.ts", () => {
    return {
        createAuthOptions: createAuthOptionsMock,
        createEndpoint: (baseHref: string, path: string, params?: any, queryParams?: any) => {
            if (params) {
                Object.keys(params).forEach(key => {
                    path = path.replace(":" + key, encodeURIComponent(params[key]));
                });
            }
            let href = `${baseHref.replace(/\/$/, "")}${path}`;
            if (queryParams) {
                const search = new URLSearchParams();
                Object.keys(queryParams).forEach(key => {
                    if (queryParams[key] !== undefined && queryParams[key] !== null && queryParams[key] !== "") {
                        search.set(key, queryParams[key]);
                    }
                });
                const query = search.toString();
                if (query) {
                    href = `${href}?${query}`;
                }
            }
            return href;
        },
        getRegistryClient: getRegistryClientMock
    };
});

vi.mock("axios", () => ({
    default: {
        get: axiosGetMock,
        post: vi.fn()
    }
}));

import { useGroupsService } from "./useGroupsService";

beforeEach(() => {
    axiosGetMock.mockReset();
    createAuthOptionsMock.mockReset();
    getRegistryClientMock.mockReset();
});

const PAGES_TO_CHECK: { page: number; expectedOffset: number }[] = [
    { page: 1, expectedOffset: 0 },
    { page: 2, expectedOffset: 10 },
    { page: 3, expectedOffset: 20 }
];

function assertConstantLimit(get: ReturnType<typeof vi.fn>): void {
    expect(get).toHaveBeenCalledTimes(PAGES_TO_CHECK.length);
    const queryParams = get.mock.calls.map(call => call[0].queryParameters);
    queryParams.forEach((params: any, i: number) => {
        expect(params.limit).toBe(10);
        expect(params.offset).toBe(PAGES_TO_CHECK[i].expectedOffset);
    });
}

// Regression test for issue #9086: the `limit` query param must always equal
// `pageSize`, regardless of which page is being requested. Previously it was
// computed as `offset + pageSize`, so it grew on every page past page 1.
describe("useGroupsService pagination", () => {
    it("sends a constant limit and correct offset across pages for getGroupArtifacts", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, artifacts: [] });
        getRegistryClientMock.mockReturnValue({
            groups: { byGroupId: () => ({ artifacts: { get } }) }
        });

        const service = useGroupsService();
        for (const { page } of PAGES_TO_CHECK) {
            const paging: Paging = { page, pageSize: 10 };
            await service.getGroupArtifacts("default", "name" as any, "asc" as any, paging);
        }

        assertConstantLimit(get);
    });

    it("sends a constant limit and correct offset across pages for getArtifactVersions", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, versions: [] });
        getRegistryClientMock.mockReturnValue({
            groups: { byGroupId: () => ({ artifacts: { byArtifactId: () => ({ versions: { get } }) } }) }
        });

        const service = useGroupsService();
        for (const { page } of PAGES_TO_CHECK) {
            const paging: Paging = { page, pageSize: 10 };
            await service.getArtifactVersions("default", "my-artifact", "version" as any, "asc" as any, paging);
        }

        assertConstantLimit(get);
    });

    it("sends a constant limit and correct offset across pages for getArtifactBranches", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, branches: [] });
        getRegistryClientMock.mockReturnValue({
            groups: { byGroupId: () => ({ artifacts: { byArtifactId: () => ({ branches: { get } }) } }) }
        });

        const service = useGroupsService();
        for (const { page } of PAGES_TO_CHECK) {
            const paging: Paging = { page, pageSize: 10 };
            await service.getArtifactBranches("default", "my-artifact", paging);
        }

        assertConstantLimit(get);
    });

    it("sends a constant limit and correct offset across pages for getArtifactBranchVersions", async () => {
        const get = vi.fn().mockResolvedValue({ count: 0, versions: [] });
        getRegistryClientMock.mockReturnValue({
            groups: {
                byGroupId: () => ({
                    artifacts: {
                        byArtifactId: () => ({
                            branches: { byBranchId: () => ({ versions: { get } }) }
                        })
                    }
                })
            }
        });

        const service = useGroupsService();
        for (const { page } of PAGES_TO_CHECK) {
            const paging: Paging = { page, pageSize: 10 };
            await service.getArtifactBranchVersions("default", "my-artifact", "my-branch", paging);
        }

        assertConstantLimit(get);
    });
});

describe("useGroupsService content loading", () => {
    it("returns artifact version content with the response content type", async () => {
        createAuthOptionsMock.mockResolvedValue({
            headers: {
                Authorization: "Bearer test-token"
            }
        });
        axiosGetMock.mockResolvedValue({
            data: "templateId: prompt\ntemplate: Hello {{name}}\n",
            headers: {
                "content-type": "application/x-yaml; charset=utf-8"
            }
        });

        const service = useGroupsService();
        const result = await service.getArtifactVersionContentWithType("default", "my-prompt", "1");

        expect(axiosGetMock).toHaveBeenCalledWith(
            "http://localhost:8080/apis/registry/v3/groups/default/artifacts/my-prompt/versions/1/content",
            expect.objectContaining({
                headers: {
                    Authorization: "Bearer test-token",
                    Accept: "*"
                },
                responseType: "text"
            })
        );
        expect(result).toEqual({
            content: "templateId: prompt\ntemplate: Hello {{name}}\n",
            contentType: "application/x-yaml; charset=utf-8"
        });
    });

    it("normalizes latest to the latest branch when loading content with type", async () => {
        createAuthOptionsMock.mockResolvedValue({});
        axiosGetMock.mockResolvedValue({
            data: "{}",
            headers: {}
        });

        const service = useGroupsService();
        await service.getArtifactVersionContentWithType(null, "my-prompt", "latest");

        expect(axiosGetMock.mock.calls[0][0]).toBe(
            "http://localhost:8080/apis/registry/v3/groups/default/artifacts/my-prompt/versions/branch%3Dlatest/content"
        );
    });
});
