// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { McpToolCompatibilityResult, McpToolCompatibilityResults } from "@sdk/lib/generated-client/models";
import { McpToolCompatibilityPanel } from "./McpToolCompatibilityPanel";

const getToolCompatibility = vi.fn();
vi.mock("@services/useMcpToolService.ts", () => ({
    useMcpToolService: () => ({ getToolCompatibility })
}));

const logError = vi.fn();
vi.mock("@services/useLoggerService.ts", () => ({
    useLoggerService: () => ({ error: logError })
}));

vi.mock("@services/useAppNavigation.ts", () => ({
    useAppNavigation: () => ({ createLink: (path: string) => path })
}));

vi.mock("@services/useArtifactTypesService.ts", () => ({
    ArtifactTypes: {
        MCP_TOOL: "MCP_TOOL"
    }
}));

type Response = McpToolCompatibilityResults | undefined;
type Deferred = { promise: Promise<Response>; resolve: (v: Response) => void; reject: (e: unknown) => void };

const deferred = (): Deferred => {
    let resolve!: (v: Response) => void;
    let reject!: (e: unknown) => void;
    const promise = new Promise<Response>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
};

const COMPATIBLE: McpToolCompatibilityResult = {
    tool: { groupId: "g", artifactId: "consumer", title: "Records Consumer" },
    verdict: "COMPATIBLE",
    reasons: [],
    limitations: []
};

const INCOMPATIBLE: McpToolCompatibilityResult = {
    tool: { groupId: "g", artifactId: "strict" },
    verdict: "INCOMPATIBLE",
    reasons: [{
        code: "TYPE_NOT_ACCEPTED",
        producerPointer: "/outputSchema/properties/records/type",
        consumerPointer: "/inputSchema/properties/records/type",
        message: "The producer's value for 'records' is not accepted by the consumer"
    }],
    limitations: []
};

const INDETERMINATE: McpToolCompatibilityResult = {
    tool: { artifactId: "open" },
    verdict: "INDETERMINATE",
    reasons: [],
    limitations: [{
        code: "UNSUPPORTED_KEYWORD",
        side: "CONSUMER",
        pointer: "/inputSchema/properties/id/enum",
        message: "'enum' is not evaluated yet"
    }]
};

const page = (results: McpToolCompatibilityResult[], count: number): McpToolCompatibilityResults => ({
    candidateTotal: 7,
    evaluated: 6,
    scanLimit: 500,
    complete: true,
    totals: { compatible: 3, incompatible: 2, indeterminate: 1 },
    count,
    limitations: [],
    results
});

/** Lets pending promise callbacks run and React apply the resulting state updates. */
const flush = async (): Promise<void> => {
    await act(async () => {
        await new Promise((r) => setTimeout(r, 0));
    });
};

const renderPanel = async (...responses: Promise<Response>[]): Promise<void> => {
    responses.forEach(response => getToolCompatibility.mockReturnValueOnce(response));
    render(
        <MemoryRouter>
            <McpToolCompatibilityPanel groupId="g" artifactId="source" version="1" />
        </MemoryRouter>
    );
    await flush();
};

const select = async (toggle: string): Promise<void> => {
    fireEvent.click(screen.getByRole("button", { name: toggle }));
    await flush();
};

const labelOf = (text: string): HTMLElement => screen.getByText(text, { selector: "table *" }).closest(".pf-v6-c-label")!;

describe("McpToolCompatibilityPanel", () => {
    afterEach(() => {
        cleanup();
        vi.clearAllMocks();
    });

    it("shows a spinner until the first response arrives", async () => {
        await renderPanel(deferred().promise);

        expect(screen.getByRole("progressbar", { name: "Loading compatibility information" })).toBeInTheDocument();
    });

    it("lists the compatible tools first, each linked to its artifact, with every verdict total", async () => {
        await renderPanel(Promise.resolve(page([COMPATIBLE], 3)));

        expect(getToolCompatibility).toHaveBeenCalledWith("g", "source", "1", ["COMPATIBLE"], { page: 1, pageSize: 20 });
        expect(screen.getByText("Compared with 6 other MCP tools.")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Compatible (3)" })).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByRole("button", { name: "Incompatible (2)" })).toHaveAttribute("aria-pressed", "false");
        expect(screen.getByRole("button", { name: "Indeterminate (1)" })).toHaveAttribute("aria-pressed", "false");
        expect(screen.getByRole("link", { name: "Records Consumer" })).toHaveAttribute("href", "/explore/g/consumer");
        expect(labelOf("Compatible")).toHaveClass("pf-m-green");
    });

    it("shows the reasons of incompatible tools and the limitations of indeterminate ones", async () => {
        await renderPanel(Promise.resolve(page([COMPATIBLE], 3)), Promise.resolve(page([INCOMPATIBLE], 2)),
            Promise.resolve(page([INDETERMINATE], 1)));

        await select("Incompatible (2)");
        expect(getToolCompatibility).toHaveBeenLastCalledWith("g", "source", "1", ["INCOMPATIBLE"], { page: 1, pageSize: 20 });
        expect(screen.getByText("The producer's value for 'records' is not accepted by the consumer")).toBeInTheDocument();
        expect(screen.getByText("/outputSchema/properties/records/type")).toBeInTheDocument();
        expect(screen.getByText("/inputSchema/properties/records/type")).toBeInTheDocument();
        expect(labelOf("Incompatible")).toHaveClass("pf-m-red");

        await select("Indeterminate (1)");
        expect(getToolCompatibility).toHaveBeenLastCalledWith("g", "source", "1", ["INDETERMINATE"], { page: 1, pageSize: 20 });
        expect(screen.getByText("'enum' is not evaluated yet")).toBeInTheDocument();
        expect(screen.getByText("/inputSchema/properties/id/enum")).toBeInTheDocument();
        expect(labelOf("Indeterminate")).toHaveClass("pf-m-orange");
        expect(screen.getByRole("link", { name: "open" })).toHaveAttribute("href", "/explore/default/open");
    });

    it("pages through the selected verdict and starts again from the first page for another verdict", async () => {
        await renderPanel(Promise.resolve(page([COMPATIBLE], 45)), Promise.resolve(page([COMPATIBLE], 45)),
            Promise.resolve(page([INCOMPATIBLE], 2)));

        fireEvent.click(screen.getAllByRole("button", { name: "Go to next page" })[0]);
        await flush();
        expect(getToolCompatibility).toHaveBeenLastCalledWith("g", "source", "1", ["COMPATIBLE"], { page: 2, pageSize: 20 });

        await select("Incompatible (2)");
        expect(getToolCompatibility).toHaveBeenLastCalledWith("g", "source", "1", ["INCOMPATIBLE"], { page: 1, pageSize: 20 });
    });

    it("keeps the verdict filter when the selected verdict has no tools", async () => {
        await renderPanel(Promise.resolve(page([], 0)));

        expect(screen.getByText("No compatible tools were found.")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Incompatible (2)" })).toBeInTheDocument();
    });

    it("says that compatibility information is unavailable when the API has none", async () => {
        await renderPanel(Promise.resolve(undefined));

        expect(screen.getByText("Compatibility information is unavailable for this tool.")).toBeInTheDocument();
    });

    it("says that compatibility information could not be loaded when the request fails", async () => {
        await renderPanel(Promise.reject(new Error("boom")));

        expect(screen.getByText("Compatibility information could not be loaded.")).toBeInTheDocument();
        expect(logError).toHaveBeenCalledTimes(1);
    });

    it("says why the tool was not compared when the response has top-level limitations", async () => {
        await renderPanel(Promise.resolve({
            evaluated: 0,
            results: [],
            limitations: [{ code: "SOURCE_HAS_NO_OUTPUT_SCHEMA", side: "PRODUCER", pointer: "/outputSchema",
                message: "The tool declares no outputSchema" }]
        }));

        expect(screen.getByText("The tool declares no outputSchema")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: /^Compatible/ })).not.toBeInTheDocument();
    });

    it("says that no other tool was compared when none was evaluated", async () => {
        await renderPanel(Promise.resolve({ evaluated: 0, totals: {}, count: 0, results: [], limitations: [] }));

        expect(screen.getByText("No other MCP tools were compared with this tool.")).toBeInTheDocument();
    });

    it("shows only the response to the latest request when responses arrive out of order", async () => {
        const incompatible = deferred();
        const indeterminate = deferred();
        await renderPanel(Promise.resolve(page([COMPATIBLE], 3)), incompatible.promise, indeterminate.promise);

        await select("Incompatible (2)");
        await select("Indeterminate (1)");
        indeterminate.resolve(page([INDETERMINATE], 1));
        await flush();
        incompatible.resolve(page([INCOMPATIBLE], 2));
        await flush();

        expect(screen.getByText("'enum' is not evaluated yet")).toBeInTheDocument();
        expect(screen.queryByText("The producer's value for 'records' is not accepted by the consumer")).not.toBeInTheDocument();
        expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    });

    it("starts another tool version from its compatible tools without showing the previous results", async () => {
        getToolCompatibility.mockReturnValueOnce(Promise.resolve(page([COMPATIBLE], 3)))
            .mockReturnValueOnce(Promise.resolve(page([INCOMPATIBLE], 2)))
            .mockReturnValueOnce(deferred().promise);
        const panel = (version: string) => (
            <MemoryRouter>
                <McpToolCompatibilityPanel groupId="g" artifactId="source" version={ version } />
            </MemoryRouter>
        );
        const { rerender } = render(panel("1"));
        await flush();
        await select("Incompatible (2)");

        rerender(panel("2"));
        await flush();

        expect(getToolCompatibility).toHaveBeenCalledTimes(3);
        expect(getToolCompatibility).toHaveBeenLastCalledWith("g", "source", "2", ["COMPATIBLE"], { page: 1, pageSize: 20 });
        expect(screen.getByRole("progressbar", { name: "Loading compatibility information" })).toBeInTheDocument();
        expect(screen.queryByText("The producer's value for 'records' is not accepted by the consumer")).not.toBeInTheDocument();
    });

    it("ignores the failure of a request that another request replaced", async () => {
        const incompatible = deferred();
        await renderPanel(Promise.resolve(page([COMPATIBLE], 3)), incompatible.promise,
            Promise.resolve(page([INDETERMINATE], 1)));

        await select("Incompatible (2)");
        await select("Indeterminate (1)");
        incompatible.reject(new Error("late"));
        await flush();

        expect(screen.getByText("'enum' is not evaluated yet")).toBeInTheDocument();
        expect(screen.queryByText("Compatibility information could not be loaded.")).not.toBeInTheDocument();
        expect(logError).not.toHaveBeenCalled();
    });
});
