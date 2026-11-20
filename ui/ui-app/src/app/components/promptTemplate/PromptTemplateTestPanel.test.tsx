// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { PromptTemplateTestPanel } from "./PromptTemplateTestPanel";

const renderPromptTemplate = vi.fn();
vi.mock("@services/useGroupsService.ts", () => ({
    useGroupsService: () => ({ renderPromptTemplate })
}));

const NOT_VALID_JSON = "Not valid JSON yet. Render will submit this as raw text.";

const panel = (variables: Record<string, any>, template: string) => (
    <PromptTemplateTestPanel
        groupId="g"
        artifactId="a"
        version="1"
        template={template}
        variables={variables}
    />
);

const typeEachPrefix = (textarea: HTMLElement, text: string): void => {
    for (let i = 1; i <= text.length; i++) {
        fireEvent.change(textarea, { target: { value: text.slice(0, i) } });
    }
};

afterEach(() => {
    cleanup();
    renderPromptTemplate.mockReset();
});

describe("PromptTemplateTestPanel object/array fields", () => {
    it("keeps compact JSON exactly as typed at every keystroke", () => {
        render(panel({ cfg: { type: "object" } }, "{{cfg}}"));
        const textarea = screen.getByLabelText("cfg");

        const typed = "{\"a\":1}";
        for (let i = 1; i <= typed.length; i++) {
            fireEvent.change(textarea, { target: { value: typed.slice(0, i) } });
            expect(textarea).toHaveValue(typed.slice(0, i));
        }
    });

    it("shows an explicit null default as the literal text null", () => {
        render(panel({ nul: { type: "object", default: null } }, "{{nul}}"));

        expect(screen.getByLabelText("nul")).toHaveValue("null");
    });

    it("pretty-prints an array default", () => {
        render(panel({ items: { type: "array", default: [1, 2] } }, "{{items}}"));

        expect(screen.getByLabelText("items")).toHaveValue("[\n  1,\n  2\n]");
    });

    it("warns while the text is not valid JSON and clears the warning once it parses", () => {
        render(panel({ cfg: { type: "object" } }, "{{cfg}}"));
        const textarea = screen.getByLabelText("cfg");
        expect(screen.queryByText(NOT_VALID_JSON)).not.toBeInTheDocument();

        typeEachPrefix(textarea, "{\"a\":");
        expect(screen.getByText(NOT_VALID_JSON)).toBeInTheDocument();

        fireEvent.change(textarea, { target: { value: "{\"a\":1}" } });
        expect(screen.queryByText(NOT_VALID_JSON)).not.toBeInTheDocument();
    });

    it("submits the parsed JSON value on Render", async () => {
        renderPromptTemplate.mockResolvedValue({ rendered: "rendered text" });
        render(panel({ cfg: { type: "object" } }, "{{cfg}}"));

        fireEvent.change(screen.getByLabelText("cfg"), { target: { value: "{\"a\":1}" } });
        fireEvent.click(screen.getByRole("button", { name: "Render" }));

        expect(await screen.findByText("rendered text")).toBeInTheDocument();
        expect(renderPromptTemplate).toHaveBeenCalledWith("g", "a", "1", { cfg: { a: 1 } });
    });
});
