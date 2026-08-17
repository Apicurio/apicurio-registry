import { describe, expect, it, vi, beforeEach } from "vitest";

vi.hoisted(() => {
    const registryConfig = { artifacts: { url: "http://localhost:8080/apis/registry/v3/" } };
    (globalThis as any).ApicurioRegistryConfig = registryConfig;
    (globalThis as any).window = { ApicurioRegistryConfig: registryConfig };
});

vi.mock("react", async (importOriginal) => {
    const actual = await importOriginal<typeof import("react")>();
    return {
        ...actual,
        useState: vi.fn((init: any) => [typeof init === "function" ? init() : init, vi.fn()]),
        useEffect: vi.fn(),
    };
});

vi.mock("@apitomy/common-ui-components", () => ({
    useAuth: () => ({})
}));

vi.mock("./CompatibleMcpToolsViewer.css", () => ({}));

vi.mock("@patternfly/react-core", () => ({
    Badge: () => null,
    Card: () => null,
    CardBody: () => null,
    CardHeader: () => null,
    CardTitle: () => null,
    DescriptionList: () => null,
    DescriptionListDescription: () => null,
    DescriptionListGroup: () => null,
    DescriptionListTerm: () => null,
    Divider: () => null,
    EmptyState: () => null,
    EmptyStateBody: () => null,
    Label: () => null,
    LabelGroup: () => null,
    Spinner: () => null,
    Title: () => null,
}));

vi.mock("@patternfly/react-icons", () => ({
    PlugIcon: () => null,
}));

import { CompatibleMcpToolsViewer } from "./CompatibleMcpToolsViewer";

const mockGetCompatibleMcpTools = vi.fn();
vi.mock("../../../services/useMcpToolsService", () => ({
    useMcpToolsService: () => ({
        getCompatibleMcpTools: mockGetCompatibleMcpTools
    })
}));

describe("CompatibleMcpToolsViewer", () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it("renders component element tree cleanly", () => {
        const element: any = CompatibleMcpToolsViewer({ groupId: "test-group", artifactId: "test-tool" });
        expect(element).toBeDefined();
        expect(element.props.className).toContain("compatible-mcp-tools-viewer");
    });
});
