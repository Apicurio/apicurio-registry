// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { VersionMetaData } from "@sdk/lib/generated-client/models";
import { VersionPage } from "./VersionPage";

const getArtifactMetaData = vi.fn();
const getArtifactVersionMetaData = vi.fn();
vi.mock("@services/useGroupsService.ts", () => ({
    useGroupsService: () => ({
        getArtifactMetaData,
        getArtifactVersionMetaData,
        getArtifactVersionContentWithType: () => Promise.resolve({ content: "{}", contentType: "application/json" })
    })
}));

const getToolCompatibility = vi.fn();
vi.mock("@services/useMcpToolService.ts", () => ({
    useMcpToolService: () => ({ getToolCompatibility })
}));

vi.mock("@services/useUserService.ts", () => ({
    useUserService: () => ({ updateCurrentUser: () => Promise.resolve({}) })
}));

vi.mock("@services/useLoggerService.ts", () => ({
    useLoggerService: () => ({ info: vi.fn(), warn: vi.fn(), error: vi.fn() })
}));

vi.mock("@services/useAppNavigation.ts", () => ({
    useAppNavigation: () => ({ createLink: (path: string) => path, navigateTo: vi.fn() })
}));

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
    VersionOverviewTabContent: ({ version }: { version: VersionMetaData }) => <p>Overview of version { version.version }</p>,
    DocumentationTabContent: () => null,
    ContentTabContent: () => null
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
    ChangeVersionStateModal: () => null,
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
});
