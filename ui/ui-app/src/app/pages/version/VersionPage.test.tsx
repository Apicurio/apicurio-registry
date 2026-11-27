// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { VersionMetaData } from "@sdk/lib/generated-client/models";
import { VersionPage } from "./VersionPage";

const getArtifactMetaData = vi.fn();
const getArtifactVersionMetaData = vi.fn();
const getArtifactVersionContentWithType = vi.fn();
const updateArtifactVersionState = vi.fn();
vi.mock("@services/useGroupsService.ts", () => ({
    useGroupsService: () => ({
        getArtifactMetaData,
        getArtifactVersionMetaData,
        getArtifactVersionContentWithType,
        updateArtifactVersionState
    })
}));

const getToolCompatibility = vi.fn();
vi.mock("@services/useMcpToolService.ts", () => ({
    useMcpToolService: () => ({ getToolCompatibility })
}));

vi.mock("@services/useSearchService.ts", () => ({
    useSearchService: () => ({ artifactPermissions: () => Promise.resolve(undefined) })
}));

vi.mock("@services/useUserService.ts", () => ({
    useUserService: () => ({ updateCurrentUser: () => Promise.resolve({}) })
}));

vi.mock("@services/useLoggerService.ts", () => ({
    useLoggerService: () => ({ info: vi.fn(), warn: vi.fn(), error: vi.fn() })
}));

vi.mock("@services/useAppNavigation.ts", async () => {
    const { useNavigate } = await import("react-router");
    return {
        useAppNavigation: () => {
            const navigate = useNavigate();
            return { createLink: (path: string) => path, navigateTo: navigate };
        }
    };
});

vi.mock("@services/useDraftsService.ts", () => ({
    useDraftsService: () => ({})
}));

vi.mock("@services/useDownloadService.ts", () => ({
    useDownloadService: () => ({})
}));

vi.mock("@services/useArtifactTypesService.ts", () => ({
    ArtifactTypes: {
        OPENAPI: "OPENAPI",
        ASYNCAPI: "ASYNCAPI",
        AGENT_CARD: "AGENT_CARD",
        MCP_TOOL: "MCP_TOOL",
        JSON: "JSON",
        MODEL_SCHEMA: "MODEL_SCHEMA",
        PROMPT_TEMPLATE: "PROMPT_TEMPLATE"
    }
}));

// The other tab contents, the page header and the modals need the whole application (Monaco
// editors, configuration, authentication); plain stand-ins keep the assertions about which tab
// is shown. The page data loader and the compatibility panel are the real ones.
vi.mock("@app/pages", async () => ({
    EXPLORE_PAGE_IDX: 0,
    PageDataLoader: (await import("@app/pages/PageDataLoader.tsx")).PageDataLoader,
    PageErrorHandler: ({ children }: { children?: ReactNode }) => <>{ children }</>,
    VersionPageHeader: () => null,
    VersionOverviewTabContent: ({ version, onChangeState }: {
        version: VersionMetaData,
        onChangeState: () => void
    }) => <>
        <p>Overview of version { version.version }</p>
        <button onClick={ onChangeState }>Change version state</button>
    </>,
    DocumentationTabContent: () => null,
    ContentTabContent: ({ versionContent }: { versionContent: string }) => <p>Content: { versionContent }</p>,
    ErrorTabContent: ({ error, children }: {
        error: { errorMessage: string, error: { detail?: string } },
        children?: ReactNode
    }) => <div role="alert">
        <p>{ error.errorMessage }</p>
        <p>{ children }</p>
        <p>{ error.error.detail }</p>
    </div>
}));

vi.mock("@app/pages/version/components/tabs/ReferencesTabContent.tsx", () => ({
    ReferencesTabContent: () => null
}));

vi.mock("@app/pages/drafts/components/modals", () => ({
    ConfirmFinalizeModal: () => null,
    FinalizeDryRunSuccessModal: () => null,
    NewDraftFromModal: () => null
}));

vi.mock("@app/pages/agents/components", () => ({
    EditAgentModal: () => null
}));

vi.mock("@app/components", () => ({
    ChangeVersionStateModal: ({ isOpen, onChangeState }: {
        isOpen: boolean,
        onChangeState: (state: string) => void
    }) => isOpen ? <button onClick={ () => onChangeState("ENABLED") }>Enable version</button> : null,
    ConfirmDeleteModal: () => null,
    EditMetaDataModal: () => null,
    GenerateClientModal: () => null,
    IfFeature: () => null,
    InvalidContentModal: () => null,
    RootPageHeader: () => null,
    TestVersionModal: () => null,
    TestVersionSuccessModal: () => null
}));

/** Lets pending promise callbacks run and React apply the resulting state updates. */
const flush = async (): Promise<void> => {
    await act(async () => {
        await new Promise((r) => setTimeout(r, 0));
    });
};

const renderCompatibilityRoute = async (artifactType: string, state: string): Promise<void> => {
    getArtifactMetaData.mockResolvedValue({ groupId: "g", artifactId: "tool", artifactType });
    getArtifactVersionMetaData.mockResolvedValue({ groupId: "g", artifactId: "tool", version: "1", artifactType, state });
    getToolCompatibility.mockResolvedValue({
        evaluated: 1,
        complete: true,
        totals: { compatible: 1, incompatible: 0, indeterminate: 0 },
        count: 1,
        limitations: [],
        results: [{ tool: { groupId: "g", artifactId: "consumer" }, verdict: "COMPATIBLE", reasons: [], limitations: [] }]
    });
    render(
        <MemoryRouter initialEntries={ ["/explore/g/tool/versions/1/compatibility"] }>
            <Routes>
                <Route path="/explore/:groupId/:artifactId/versions/:version/compatibility" element={ <VersionPage /> } />
            </Routes>
        </MemoryRouter>
    );
    await flush();
    await flush();
};

describe("VersionPage", () => {
    beforeEach(() => {
        getArtifactVersionContentWithType.mockResolvedValue({ content: "{}", contentType: "application/json" });
        updateArtifactVersionState.mockResolvedValue(undefined);
    });

    afterEach(() => {
        cleanup();
        vi.clearAllMocks();
    });

    it("shows the compatible tools of an MCP tool version on the compatibility route", async () => {
        await renderCompatibilityRoute("MCP_TOOL", "ENABLED");

        expect(screen.getByRole("tab", { name: "Compatible tools" })).toHaveAttribute("aria-selected", "true");
        expect(screen.getByText("Compared with 1 other MCP tool.")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "consumer" })).toBeInTheDocument();
        expect(screen.queryByText("Overview of version 1")).not.toBeInTheDocument();
    });

    it.each([
        [ "a non-MCP artifact", "OPENAPI", "ENABLED" ],
        [ "a disabled MCP tool version", "MCP_TOOL", "DISABLED" ]
    ])("shows the overview of %s on the compatibility route", async (_case, artifactType, state) => {
        await renderCompatibilityRoute(artifactType, state);

        expect(screen.getByRole("tab", { name: "Overview" })).toHaveAttribute("aria-selected", "true");
        expect(screen.getByText("Overview of version 1")).toBeVisible();
        expect(screen.queryByRole("tab", { name: "Compatible tools" })).not.toBeInTheDocument();
        expect(getToolCompatibility).not.toHaveBeenCalled();
    });

    it("renders disabled version content failures as an error state", async () => {
        getArtifactMetaData.mockResolvedValue({ groupId: "g", artifactId: "a", artifactType: "JSON" });
        getArtifactVersionMetaData.mockResolvedValue({
            groupId: "g", artifactId: "a", version: "1", artifactType: "JSON", state: "DISABLED"
        });
        getArtifactVersionContentWithType.mockRejectedValue({
            responseStatusCode: 404,
            nameEscaped: "VersionContentNotAvailableException",
            detail: "Content for version '1' is not available while its state is 'DISABLED'."
        });

        render(
            <MemoryRouter initialEntries={ ["/explore/g/a/versions/1/content"] }>
                <Routes>
                    <Route path="/explore/:groupId/:artifactId/versions/:version/*" element={ <VersionPage /> } />
                </Routes>
            </MemoryRouter>
        );
        await flush();
        await flush();

        expect(screen.getByRole("alert")).toHaveTextContent("Content is not available while this version is disabled.");
        expect(screen.getByRole("alert")).toHaveTextContent("Re-enable the version to view its content.");
        expect(screen.getByRole("alert")).toHaveTextContent("Content for version '1' is not available");
        expect(screen.queryByText(/Content: Artifact version content not available/)).not.toBeInTheDocument();
    });

    it("refetches content after re-enabling a disabled version", async () => {
        getArtifactMetaData.mockResolvedValue({ groupId: "g", artifactId: "a", artifactType: "JSON" });
        getArtifactVersionMetaData.mockResolvedValue({
            groupId: "g", artifactId: "a", version: "1", artifactType: "JSON", state: "DISABLED"
        });
        getArtifactVersionContentWithType
            .mockRejectedValueOnce({ responseStatusCode: 404, nameEscaped: "VersionContentNotAvailableException" })
            .mockResolvedValueOnce({ content: "refetched content", contentType: "application/json" });

        render(
            <MemoryRouter initialEntries={ ["/explore/g/a/versions/1/content"] }>
                <Routes>
                    <Route path="/explore/:groupId/:artifactId/versions/:version/*" element={ <VersionPage /> } />
                </Routes>
            </MemoryRouter>
        );
        await flush();
        await flush();

        fireEvent.click(screen.getByRole("tab", { name: "Overview" }));
        fireEvent.click(screen.getByRole("button", { name: "Change version state" }));
        fireEvent.click(screen.getByRole("button", { name: "Enable version" }));

        await waitFor(() => expect(updateArtifactVersionState).toHaveBeenCalledWith("g", "a", "1", "ENABLED"));
        await waitFor(() => expect(getArtifactVersionContentWithType).toHaveBeenCalledTimes(2));
        fireEvent.click(screen.getByRole("tab", { name: "Content" }));
        expect(screen.getByText("Content: refetched content")).toBeVisible();
    });
});
