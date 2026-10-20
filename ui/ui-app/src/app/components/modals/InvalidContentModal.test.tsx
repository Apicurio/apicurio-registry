// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { RuleViolationProblemDetails } from "@sdk/lib/generated-client/models";
import { InvalidContentModal } from "./InvalidContentModal";

const getArtifactVersionContent = vi.fn();
vi.mock("@services/useGroupsService.ts", () => ({
    useGroupsService: () => ({ getArtifactVersionContent })
}));

// content.utils imports this module only for its ArtifactTypes constants; the real one loads the
// config service, which needs the browser's registry config global.
vi.mock("@services/useArtifactTypesService.ts", () => ({
    ArtifactTypes: {}
}));

// The real editors mount Monaco. The stand-in hands the report a fake editor and Monaco instance on
// mount, so the tests can check the markers it sets and where a click scrolls.
const monaco = vi.hoisted(() => ({
    setModelMarkers: vi.fn(),
    revealRangeInCenter: vi.fn(),
    setSelection: vi.fn()
}));
vi.mock("@app/components/codeEditor/RegistryEditors.tsx", async () => {
    const { useEffect } = await import("react");
    return {
        RegistryCodeEditor: (props: {
            value: string,
            options?: { readOnly?: boolean },
            onMount?: (editor: unknown, instance: unknown) => void
        }) => {
            useEffect(() => {
                props.onMount?.(
                    { getModel: () => ({}), revealRangeInCenter: monaco.revealRangeInCenter, setSelection: monaco.setSelection },
                    { editor: { setModelMarkers: monaco.setModelMarkers }, MarkerSeverity: { Error: 8 } }
                );
            }, []);
            return <pre data-testid="submitted-content" data-readonly={String(props.options?.readOnly)}>{props.value}</pre>;
        }
    };
});
vi.mock("@app/components/common/DiffView.tsx", () => ({
    DiffView: (props: { original: string; modified: string, originalMarkers?: unknown[], modifiedMarkers?: unknown[] }) => (
        <pre data-testid="compared-content"
            data-original-markers={JSON.stringify(props.originalMarkers ?? [])}
            data-modified-markers={JSON.stringify(props.modifiedMarkers ?? [])}>{`${props.original}|${props.modified}`}</pre>
    )
}));

const SUBMITTED = "{\"type\":\"record\",\"name\":\"User\",\"fields\":[]}";

// Pretty-printed, so each location has its own line.
const SUBMITTED_LINES = [
    "{",
    "  \"type\": \"record\",",
    "  \"name\": \"User\",",
    "  \"fields\": [",
    "    { \"name\": \"a\", \"type\": \"string\" },",
    "    { \"name\": \"b\", \"type\": \"string\" }",
    "  ]",
    "}"
].join("\n");
const EXISTING_LINES = [
    "{",
    "  \"type\": \"record\",",
    "  \"name\": \"User\",",
    "  \"fields\": [",
    "    { \"name\": \"a\", \"type\": \"string\" }",
    "  ]",
    "}"
].join("\n");

type Marker = { startLineNumber: number, endLineNumber: number, message: string, severity?: number };
const lastMarkers = (): Marker[] => {
    const calls = monaco.setModelMarkers.mock.calls;
    return calls.length === 0 ? [] : calls[calls.length - 1][2];
};
const lines = (markers: Marker[]): [number, number, string][] => markers.map(m => [m.startLineNumber, m.endLineNumber, m.message]);

const modal = (error: Partial<RuleViolationProblemDetails>, content?: string) => (
    <InvalidContentModal
        isOpen={true}
        error={error as RuleViolationProblemDetails}
        content={content}
        contentType="application/json"
        groupId="g"
        artifactId="a"
        onClose={() => {}}
    />
);

const groupTexts = (): string[] => screen.getAllByTestId("compatibility-report-group").map(g => g.textContent ?? "");

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("InvalidContentModal", () => {
    it("shows today's rows and the submitted content when the new fields are absent", () => {
        render(modal({
            nameEscaped: "RuleViolationException",
            causes: [
                { description: "b", context: "/fields/1" },
                { description: "no location here" }
            ]
        }, SUBMITTED));

        expect(groupTexts()).toEqual(["/fields/1b", "no location here"]);
        expect(screen.getByText("/fields/1").tagName).toBe("B");
        expect(screen.getByTestId("submitted-content")).toHaveTextContent(SUBMITTED);
        expect(screen.getByTestId("submitted-content")).toHaveAttribute("data-readonly", "true");
    });

    it("keeps causes that share a context on separate rows when the new fields are absent", () => {
        render(modal({
            nameEscaped: "RuleViolationException",
            causes: [{ description: "first", context: "/fields" }, { description: "second", context: "/fields" }]
        }, SUBMITTED));

        expect(groupTexts()).toEqual(["/fieldsfirst", "/fieldssecond"]);
    });

    it("titles the empty pointer as the whole document", () => {
        render(modal({
            additionalData: { ruleViolation: { ruleType: "COMPATIBILITY" } },
            causes: [{ description: "root changed", additionalData: { proposed: { pointer: "" } } }]
        }, SUBMITTED));

        expect(groupTexts()).toEqual(["(whole document)root changed"]);
    });

    it("groups causes by proposed location and labels what each one breaks", () => {
        render(modal({
            nameEscaped: "RuleViolationException",
            additionalData: { ruleViolation: { ruleType: "COMPATIBILITY" } },
            causes: [
                { description: "maxLength decreased", context: "/x", additionalData: { direction: "BACKWARD", proposed: { pointer: "/properties/name" } } },
                { description: "type changed", context: "/y", additionalData: { direction: "BOTH", proposed: { pointer: "/properties/name" } } },
                { description: "enum value removed", context: "/properties/kind" }
            ]
        }, SUBMITTED));

        expect(groupTexts()).toEqual([
            "/properties/namemaxLength decreasedBreaks backward compatibilitytype changedBreaks backward and forward compatibility",
            "/properties/kindenum value removed"
        ]);
    });

    it("lists a location in a referenced artifact on its own, naming the reference", () => {
        render(modal({
            additionalData: { ruleViolation: { ruleType: "COMPATIBILITY" } },
            causes: [
                { description: "in document", additionalData: { proposed: { pointer: "/p" } } },
                { description: "in reference", additionalData: { proposed: { pointer: "/p", reference: "common.json" } } }
            ]
        }, SUBMITTED));

        expect(groupTexts()).toEqual(["/pin document", "/pIn referenced artifact common.jsonin reference"]);
    });

    it("keeps the plain list for a validity violation", () => {
        render(modal({
            nameEscaped: "RuleViolationException",
            additionalData: { ruleViolation: { ruleType: "VALIDITY" } },
            causes: [{ description: "Syntax error", context: "line 3" }]
        }, SUBMITTED));

        expect(screen.queryByTestId("compatibility-report")).not.toBeInTheDocument();
        expect(screen.getByText("line 3")).toBeInTheDocument();
        expect(screen.getByText("Syntax error")).toBeInTheDocument();
    });

    it("keeps the plain list for a validity violation identified only by ruleViolation", () => {
        // After #9657 the exception name changes, so ruleViolation is the only signal.
        render(modal({
            nameEscaped: "RuleViolation",
            additionalData: { ruleViolation: { ruleType: "VALIDITY" } },
            causes: [{ description: "Syntax error", context: "line 3" }],
            detail: "raw detail"
        }, SUBMITTED));

        expect(screen.queryByTestId("compatibility-report")).not.toBeInTheDocument();
        expect(screen.getByText("Syntax error")).toBeInTheDocument();
        expect(screen.queryByText("raw detail")).not.toBeInTheDocument();
    });

    it("reads the causes from ruleViolation, the shape the API moves to in 3.5", () => {
        render(modal({
            additionalData: {
                ruleViolation: {
                    ruleType: "COMPATIBILITY",
                    causes: [{ description: "Field 'b' was added without a default.", context: "/fields/1", direction: "BACKWARD" }]
                }
            }
        }, SUBMITTED));

        expect(groupTexts()).toEqual(["/fields/1Field 'b' was added without a default.Breaks backward compatibility"]);
    });

    it("shows the detail text for an error that is not a rule violation", () => {
        render(modal({ nameEscaped: "ArtifactAlreadyExistsException", detail: "Artifact a already exists." }));

        expect(screen.queryByTestId("compatibility-report")).not.toBeInTheDocument();
        expect(screen.getByText("Artifact a already exists.")).toBeInTheDocument();
    });

    it("offers the compared version only when the server names it, and loads it on request", async () => {
        getArtifactVersionContent.mockResolvedValueOnce("EXISTING");
        render(modal({
            additionalData: { ruleViolation: { ruleType: "COMPATIBILITY" } },
            causes: [{ description: "b", context: "/fields/1", additionalData: { existing: { pointer: "/fields", version: "3" } } }]
        }, SUBMITTED));

        await act(async () => {
            fireEvent.click(screen.getByTestId("compatibility-report-compare"));
        });

        expect(getArtifactVersionContent).toHaveBeenCalledTimes(1);
        expect(getArtifactVersionContent).toHaveBeenCalledWith("g", "a", "3");
        expect(screen.getByTestId("compared-content")).toHaveTextContent(`EXISTING|${SUBMITTED}`);
    });

    it("says it is loading the compared version, and loads it only once across toggles", async () => {
        let resolve!: (v: string) => void;
        getArtifactVersionContent.mockReturnValueOnce(new Promise<string>(r => { resolve = r; }));
        render(modal({
            additionalData: { ruleViolation: { ruleType: "COMPATIBILITY" } },
            causes: [{ description: "b", context: "/fields/1", additionalData: { existing: { version: "3" } } }]
        }, SUBMITTED));
        const compare = screen.getByTestId("compatibility-report-compare");

        fireEvent.click(compare);
        expect(screen.getByTestId("compatibility-report-compare-loading")).toHaveTextContent("Loading version 3...");

        await act(async () => resolve("EXISTING"));
        expect(screen.getByTestId("compared-content")).toHaveTextContent(`EXISTING|${SUBMITTED}`);

        fireEvent.click(compare);
        expect(screen.getByTestId("submitted-content")).toHaveTextContent(SUBMITTED);
        await act(async () => {
            fireEvent.click(compare);
        });
        expect(screen.getByTestId("compared-content")).toHaveTextContent(`EXISTING|${SUBMITTED}`);
        expect(getArtifactVersionContent).toHaveBeenCalledTimes(1);
    });

    it("keeps showing the submitted content, and says so, when the compared version cannot be loaded", async () => {
        getArtifactVersionContent.mockRejectedValueOnce(new Error("404"));
        render(modal({
            additionalData: { ruleViolation: { ruleType: "COMPATIBILITY" } },
            causes: [{ description: "b", context: "/fields/1", additionalData: { existing: { version: "3" } } }]
        }, SUBMITTED));

        await act(async () => {
            fireEvent.click(screen.getByTestId("compatibility-report-compare"));
        });

        expect(screen.getByText("Version 3 could not be loaded.")).toBeInTheDocument();
        expect(screen.getByText("Submitted content")).toBeInTheDocument();
        expect(screen.getByTestId("submitted-content")).toHaveTextContent(SUBMITTED);
        expect(screen.queryByTestId("compared-content")).not.toBeInTheDocument();
    });

    it("has no compare control when the compared version is not known", () => {
        render(modal({
            nameEscaped: "RuleViolationException",
            causes: [{ description: "b", context: "/fields/1" }]
        }, SUBMITTED));

        expect(screen.queryByTestId("compatibility-report-compare")).not.toBeInTheDocument();
    });

    it("shows the causes without a content section when the flow had no content to keep", () => {
        render(modal({
            nameEscaped: "RuleViolationException",
            causes: [{ description: "b", context: "/fields/1" }]
        }));

        expect(groupTexts()).toEqual(["/fields/1b"]);
        expect(screen.queryByTestId("compatibility-report-content")).not.toBeInTheDocument();
    });
    describe("highlighting", () => {
        it("marks each cause in the submitted content, using context when there is no proposed location", () => {
            render(modal({
                ruleViolation: { ruleType: "COMPATIBILITY" },
                causes: [
                    { description: "b has no default", context: "/fields/1/type", additionalData: { proposed: { pointer: "/fields/1" } } },
                    { description: "name changed", context: "/name" }
                ]
            } as unknown as Partial<RuleViolationProblemDetails>, SUBMITTED_LINES));

            expect(lines(lastMarkers())).toEqual([[6, 6, "b has no default"], [3, 3, "name changed"]]);
            expect(lastMarkers()[0].severity).toBe(8);
        });

        it("gives no highlight to a location in a referenced artifact or a context that is not a pointer", () => {
            render(modal({
                ruleViolation: { ruleType: "COMPATIBILITY" },
                causes: [
                    { description: "in the reference", additionalData: { proposed: { pointer: "/fields/1", reference: "common.json" } } },
                    { description: "an OpenAPI path", context: "/pets/{petId}" },
                    { description: "a message name", context: "Person" }
                ]
            } as unknown as Partial<RuleViolationProblemDetails>, SUBMITTED_LINES));

            expect(lastMarkers()).toEqual([]);
            expect(screen.queryAllByTestId("compatibility-report-location")).toHaveLength(0);
            expect(groupTexts()).toHaveLength(3);
        });

        it("scrolls the submitted content to a location when its title is clicked", () => {
            render(modal({
                ruleViolation: { ruleType: "COMPATIBILITY" },
                causes: [{ description: "b has no default", additionalData: { proposed: { pointer: "/fields/1" } } }]
            } as unknown as Partial<RuleViolationProblemDetails>, SUBMITTED_LINES));

            fireEvent.click(screen.getByTestId("compatibility-report-location"));

            expect(monaco.revealRangeInCenter).toHaveBeenCalledTimes(1);
            expect(monaco.revealRangeInCenter.mock.calls[0][0]).toMatchObject({ startLineNumber: 6, endLineNumber: 6 });
            expect(monaco.setSelection.mock.calls[0][0]).toMatchObject({ startLineNumber: 6, endLineNumber: 6 });
        });

        it("marks both documents when comparing, the compared version at its existing location", async () => {
            getArtifactVersionContent.mockResolvedValueOnce(EXISTING_LINES);
            render(modal({
                ruleViolation: { ruleType: "COMPATIBILITY" },
                causes: [{
                    description: "b has no default",
                    additionalData: { proposed: { pointer: "/fields/1" }, existing: { pointer: "/fields", version: "3" } }
                }]
            } as unknown as Partial<RuleViolationProblemDetails>, SUBMITTED_LINES));

            await act(async () => {
                fireEvent.click(screen.getByTestId("compatibility-report-compare"));
            });

            const compared = screen.getByTestId("compared-content");
            expect(lines(JSON.parse(compared.getAttribute("data-original-markers") ?? "[]"))).toEqual([[4, 6, "b has no default"]]);
            expect(lines(JSON.parse(compared.getAttribute("data-modified-markers") ?? "[]"))).toEqual([[6, 6, "b has no default"]]);
        });
    });
});
