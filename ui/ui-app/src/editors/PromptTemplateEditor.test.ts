// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import React from "react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { render, cleanup, act, fireEvent, screen } from "@testing-library/react";
import type { editor } from "monaco-editor";
import YAML from "yaml";

// Mock the artifact types service module: content.utils imports it only for the
// ArtifactTypes constants, but loading the real module pulls PatternFly CSS into the test runner.
vi.mock("@services/useArtifactTypesService.ts", () => ({
    ArtifactTypes: {
        PROTOBUF: "PROTOBUF",
        PROMPT_TEMPLATE: "PROMPT_TEMPLATE"
    }
}));

// Mock RegistryCodeEditor to capture props and allow exercising onMount / onChange callbacks.
let capturedEditorProps: any = null;
vi.mock("@app/components/codeEditor/RegistryEditors.tsx", () => ({
    RegistryCodeEditor: (props: any) => {
        capturedEditorProps = props;
        return React.createElement("div", { "data-testid": "mocked-registry-editor" });
    }
}));

// Setup JSDOM polyfills required by Monaco initialization
if (typeof document !== "undefined" && !(document as any).queryCommandSupported) {
    (document as any).queryCommandSupported = () => false;
}
if (typeof window !== "undefined" && !window.matchMedia) {
    window.matchMedia = (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false
    } as any);
}
// jsdom reports itself as WebKit, so Monaco writes to the clipboard on every click (Safari workaround).
if (typeof navigator !== "undefined" && !navigator.clipboard) {
    Object.defineProperty(navigator, "clipboard", {
        configurable: true,
        value: { write: () => Promise.resolve(), writeText: () => Promise.resolve() }
    });
}
if (typeof (globalThis as any).ClipboardItem === "undefined") {
    (globalThis as any).ClipboardItem = class {
        // Like the browser, own the pending values, so Monaco cancelling one is not an unhandled rejection.
        constructor(items: Record<string, unknown>) {
            Object.values(items).forEach(value => Promise.resolve(value).catch(() => undefined));
        }
    };
}

const monaco = await import("monaco-editor");

import { PROMPT_TEMPLATE_EDITOR_ARIA_LABEL, PromptTemplateEditor } from "./PromptTemplateEditor.tsx";
import { contentToString } from "@utils/content.utils.ts";
import {
    PROMPT_TEMPLATE_LANGUAGE_ID,
    registerPromptTemplate
} from "./PromptTemplateLanguage.ts";

const YAML_TEMPLATE = `templateId: greeting
name: Greeting Template
template: "Hello, {{name}}! Welcome to {{place}}."
variables:
  name:
    type: string
    required: true
  place:
    type: string
    default: Wonderland`;

const JSON_TEMPLATE = `{
  "templateId": "qa-prompt",
  "name": "Q&A Prompt",
  "template": "Question: {{question}}\\nContext: {{context}}",
  "variables": {
    "question": { "type": "string" },
    "context": { "type": "string" }
  }
}`;

const YAML_WITH_CONDITIONALS = `templateId: combined
template: "You are a {{role}} assistant.{{#if includeContext}} Context: {{context}}.{{/if}} Question: {{question}}"
variables:
  role:
    type: string
  includeContext:
    type: boolean
  context:
    type: string
  question:
    type: string`;

const YAML_WITH_UNLESS = `templateId: unless-test
template: "{{#unless premium}}Free plan — upgrade to unlock.{{/unless}}"`;

const YAML_WITH_MISSING = `templateId: greeting
template: "Hello, {{name}}! Your code is {{code}}."
variables:
  name:
    type: string
`;

const YAML_NO_TEMPLATE = `templateId: no-template
name: No Template
variables:
  name:
    type: string
`;

const INVALID_YAML = `templateId: wip
template: "Hello {{name
  this line is broken yaml
  variables:
    name:
      type: string
      required:`;

const YAML_WITH_METADATA = `templateId: rich
name: Rich Template
description: A template with many fields
template: "Hello {{name}}"
variables:
  name:
    type: string
metadata:
  author: reviewer
  createdAt: "2024-01-15"
  tags:
    - greeting
    - test
  recommendedModels:
    - gpt-4o
    - claude-3
outputSchema:
  type: object
  properties:
    result:
      type: string
mcp:
  enabled: true
  name: greeting-prompt
  description: A greeting prompt for MCP`;

/**
 * Creates a real Monaco text model in the prompt-template language and a minimal editor facade
 * exposing it, so synchronization and undo history run against Monaco itself.
 */
function createEditorHarness(text: string) {
    const model = monaco.editor.createModel(text, PROMPT_TEMPLATE_LANGUAGE_ID);
    const editor = { getModel: () => model, getValue: () => model.getValue() };
    return { model, editor };
}

function renderEditor(content: string, key?: string) {
    return React.createElement(PromptTemplateEditor, {
        key,
        content: { content, contentType: "application/x-yaml" } as any,
        onChange: vi.fn()
    });
}

const RECOVERED_YAML = `templateId: greeting
name: Greeting Template
template: "Hi {{name}}, recovered edit."`;

// Comments, blank lines, compact collections and literals such as 1.0e3 would not survive re-serialization.
const SUPPORT_YAML = `# Customer support prompt
templateId: support-reply
name: Support Reply
description: "Replies to {{customer}} tickets"

template: |
  You are a support agent for {{product}}.
  Greet {{customer}} politely.
  {{#if urgent}}Escalate immediately.{{/if}}
variables:
  product:
    type: string
  customer:
    type: string   # shown in the UI
  urgent:
    type: boolean
    default: false
metadata:
  tags: [support, email]`;

const SUPPORT_JSON = `{
  "templateId": "support-reply",
  "description":"Replies to {{customer}} tickets",
  "template": "You are a support agent for {{product}}.\\nGreet {{customer}} politely.",

  "variables": {"product": {"type": "string"}, "customer": {"type": "string", "maxLength": 1.0e3}},
  "temperature": 0.50
}`;

function renderDraft(draft: { content: string; contentType: string }, onChange: (value: string) => void) {
    return React.createElement(PromptTemplateEditor, { content: draft as any, onChange });
}

/** Forwards model changes to the editor's onChange the way "@monaco-editor/react" does for each keystroke. */
function forwardChanges(model: editor.ITextModel) {
    model.onDidChangeContent(() => capturedEditorProps.onChange(model.getValue()));
}

/** Replaces the first match of `search` as one undoable edit, like typing over a selection. */
function typeOver(model: editor.ITextModel, search: string, replacement: string) {
    const [match] = model.findMatches(search, false, false, true, null, false);
    model.pushEditOperations([], [{ range: match.range, text: replacement }], () => null);
    model.pushStackElement();
}

describe("PromptTemplateEditor — Mounted Component Lifecycle & Synchronization", () => {
    const models: any[] = [];

    beforeEach(() => {
        capturedEditorProps = null;
        registerPromptTemplate(monaco);
    });

    afterEach(() => {
        cleanup();
        models.splice(0).forEach(model => model.dispose());
    });

    function mount(text: string) {
        const harness = createEditorHarness(text);
        models.push(harness.model);
        act(() => {
            capturedEditorProps.onMount(harness.editor);
        });
        return harness;
    }

    it("mounts with the raw content, prompt-template language and an accessible label", () => {
        render(renderEditor(YAML_TEMPLATE));

        expect(capturedEditorProps.defaultLanguage).toBe(PROMPT_TEMPLATE_LANGUAGE_ID);
        expect(capturedEditorProps.defaultValue).toBe(YAML_TEMPLATE);
        expect(capturedEditorProps.options.ariaLabel).toBe(PROMPT_TEMPLATE_EDITOR_ARIA_LABEL);
        expect(capturedEditorProps.options.wordWrap).toBe("on");

        const { model } = mount(YAML_TEMPLATE);
        expect(model.getValue()).toBe(YAML_TEMPLATE);
    });

    it("does not touch the model or undo history when rerendered with unchanged content", () => {
        const { rerender } = render(renderEditor(YAML_TEMPLATE));
        const { model } = mount(YAML_TEMPLATE);
        const versionBefore = model.getAlternativeVersionId();

        act(() => {
            rerender(renderEditor(YAML_TEMPLATE));
        });

        expect(model.getValue()).toBe(YAML_TEMPLATE);
        expect(model.getAlternativeVersionId()).toBe(versionBefore);
    });

    it("applies a restored draft as an undoable edit so the user can undo back and redo again", async () => {
        const { rerender } = render(renderEditor(YAML_TEMPLATE));
        const { model } = mount(YAML_TEMPLATE);

        act(() => {
            rerender(renderEditor(RECOVERED_YAML));
        });
        expect(model.getValue()).toBe(RECOVERED_YAML);

        await model.undo();
        expect(model.getValue()).toBe(YAML_TEMPLATE);

        await model.redo();
        expect(model.getValue()).toBe(RECOVERED_YAML);
    });

    it("keeps earlier user edits undoable after a restored draft is applied", async () => {
        const { rerender } = render(renderEditor(YAML_TEMPLATE));
        const { model } = mount(YAML_TEMPLATE);
        const userEdit = YAML_TEMPLATE.replace("Greeting Template", "Edited Template");

        model.pushEditOperations([], [{ range: model.getFullModelRange(), text: userEdit }], () => null);
        model.pushStackElement();
        act(() => {
            rerender(renderEditor(RECOVERED_YAML));
        });

        await model.undo();
        expect(model.getValue()).toBe(userEdit);
        await model.undo();
        expect(model.getValue()).toBe(YAML_TEMPLATE);
    });

    it("starts a fresh editor and undo history when switching to another draft", async () => {
        const draftB = `templateId: other
template: "{{#unless premium}}Upgrade{{/unless}}"`;
        const { rerender } = render(renderEditor(YAML_TEMPLATE, "draft-a"));
        const first = mount(YAML_TEMPLATE);
        first.model.pushEditOperations([], [{ range: first.model.getFullModelRange(), text: YAML_WITH_METADATA }], () => null);

        act(() => {
            rerender(renderEditor(draftB, "draft-b"));
        });
        // EditorPage keys the editor by draft, so a new draft mounts a new Monaco editor with its own content.
        expect(capturedEditorProps.defaultValue).toBe(draftB);
        const second = mount(draftB);

        await second.model.undo();
        expect(second.model.getValue()).toBe(draftB);
        expect(first.model.getValue()).toBe(YAML_WITH_METADATA);
    });

    it("passes malformed intermediate input directly to props.onChange unchanged without throwing", () => {
        const mockOnChange = vi.fn();
        render(
            React.createElement(PromptTemplateEditor, {
                content: { content: INVALID_YAML, contentType: "application/x-yaml" } as any,
                onChange: mockOnChange
            })
        );

        const midEditBroken = INVALID_YAML + "\n  incomplete_placeholder: {{broken";
        act(() => {
            expect(() => capturedEditorProps.onChange(midEditBroken)).not.toThrow();
        });

        expect(mockOnChange).toHaveBeenCalledTimes(1);
        expect(mockOnChange).toHaveBeenCalledWith(midEditBroken);
    });

    it("recovers highlighting after incomplete intermediate input becomes valid using the real tokenizer", () => {
        // Step A: Incomplete input mid-typing
        const incompleteSource = "template: \"Hello {{incomplete";
        const incompleteTokens = monaco.editor.tokenize(incompleteSource, PROMPT_TEMPLATE_LANGUAGE_ID);
        const hasVariableBefore = incompleteTokens.some(line => line.some(t => t.type.includes("variable")));
        expect(hasVariableBefore).toBe(false);

        // Step B: Input completed and valid
        const completedSource = "template: \"Hello {{incomplete}}\"";
        const completedTokens = monaco.editor.tokenize(completedSource, PROMPT_TEMPLATE_LANGUAGE_ID);
        const hasVariableAfter = completedTokens.some(line => line.some(t => t.type.includes("variable")));
        expect(hasVariableAfter).toBe(true);

        // Step C: Conditional incomplete -> valid recovery
        const incompleteIf = "template: \"{{#if flag";
        const ifTokensBefore = monaco.editor.tokenize(incompleteIf, PROMPT_TEMPLATE_LANGUAGE_ID);
        expect(ifTokensBefore.some(line => line.some(t => t.type.includes("keyword")))).toBe(false);

        const completeIf = "template: \"{{#if flag}}\"";
        const ifTokensAfter = monaco.editor.tokenize(completeIf, PROMPT_TEMPLATE_LANGUAGE_ID);
        expect(ifTokensAfter.some(line => line.some(t => t.type.includes("keyword")))).toBe(true);
    });

    it("passes initial JSON artifact string to editor defaultValue unchanged", () => {
        render(
            React.createElement(PromptTemplateEditor, {
                content: { content: JSON_TEMPLATE, contentType: "application/json" } as any,
                onChange: vi.fn()
            })
        );

        expect(capturedEditorProps.defaultValue).toBe(JSON_TEMPLATE);
    });

    // EditorPage re-renders on every change but keeps the same draftContent object until it loads,
    // recovers, saves or formats a draft, so these tests rerender with the same object.
    describe("editing through the Monaco model", () => {
        it("reports a YAML template edit without changing other fields, comments or blank lines", async () => {
            const draft = { content: SUPPORT_YAML, contentType: "application/x-yaml" };
            const onChange = vi.fn();
            const { rerender } = render(renderDraft(draft, onChange));
            const { model } = mount(SUPPORT_YAML);
            forwardChanges(model);

            typeOver(model, "politely", "by name");

            const edited = SUPPORT_YAML.replace("politely", "by name");
            expect(onChange).toHaveBeenCalledTimes(1);
            expect(onChange).toHaveBeenLastCalledWith(edited);
            const { template: templateBefore, ...fieldsBefore } = YAML.parse(SUPPORT_YAML);
            const { template: templateAfter, ...fieldsAfter } = YAML.parse(edited);
            expect(templateAfter).toBe(templateBefore.replace("politely", "by name"));
            expect(fieldsAfter).toEqual(fieldsBefore);

            act(() => {
                rerender(renderDraft(draft, onChange));
            });
            expect(model.getValue()).toBe(edited);

            await model.undo();
            expect(onChange).toHaveBeenLastCalledWith(SUPPORT_YAML);
            await model.redo();
            expect(onChange).toHaveBeenLastCalledWith(edited);
        });

        it("reports a JSON template edit without reformatting the rest of the document", () => {
            const draft = { content: SUPPORT_JSON, contentType: "application/json" };
            const onChange = vi.fn();
            const { rerender } = render(renderDraft(draft, onChange));
            const { model } = mount(SUPPORT_JSON);
            forwardChanges(model);

            typeOver(model, "politely", "by name");

            const edited = SUPPORT_JSON.replace("politely", "by name");
            expect(onChange).toHaveBeenCalledTimes(1);
            expect(onChange).toHaveBeenLastCalledWith(edited);
            const { template: templateBefore, ...fieldsBefore } = JSON.parse(SUPPORT_JSON);
            const { template: templateAfter, ...fieldsAfter } = JSON.parse(edited);
            expect(templateAfter).toBe(templateBefore.replace("politely", "by name"));
            expect(fieldsAfter).toEqual(fieldsBefore);

            act(() => {
                rerender(renderDraft(draft, onChange));
            });
            expect(model.getValue()).toBe(edited);
        });

        it.each([
            ["YAML", SUPPORT_YAML, "application/x-yaml", "\"", (text: string) => YAML.parse(text)],
            ["JSON", SUPPORT_JSON, "application/json", "\",", (text: string) => JSON.parse(text)]
        ])("keeps invalid intermediate %s editable and never puts the stale draft back", (_format, source, contentType, closing, parse) => {
            const draft = { content: source, contentType };
            const onChange = vi.fn();
            const { rerender } = render(renderDraft(draft, onChange));
            const { model } = mount(source);
            forwardChanges(model);

            // Delete the description's closing quote, keep editing the template, then type the quote back.
            typeOver(model, `tickets${closing}`, "tickets");
            const broken = source.replace(`tickets${closing}`, "tickets");
            expect(onChange).toHaveBeenLastCalledWith(broken);
            expect(() => parse(broken)).toThrow();

            act(() => {
                rerender(renderDraft(draft, onChange));
            });
            expect(model.getValue()).toBe(broken);

            typeOver(model, "politely", "by name");
            typeOver(model, "tickets", `tickets${closing}`);

            const fixed = source.replace("politely", "by name");
            expect(onChange).toHaveBeenCalledTimes(3);
            expect(onChange).toHaveBeenLastCalledWith(fixed);
            expect(parse(fixed).template).toContain("Greet {{customer}} by name.");
        });

        it("keeps the edit and its undo history when EditorPage stores the saved text", async () => {
            const draft = { content: SUPPORT_YAML, contentType: "application/x-yaml" };
            const { rerender } = render(renderDraft(draft, vi.fn()));
            const { model } = mount(SUPPORT_YAML);
            typeOver(model, "politely", "by name");
            const saved = SUPPORT_YAML.replace("politely", "by name");
            const versionAfterEdit = model.getAlternativeVersionId();

            // A successful save replaces draftContent with the text that was just saved.
            act(() => {
                rerender(renderDraft({ ...draft, content: saved }, vi.fn()));
            });
            expect(model.getValue()).toBe(saved);
            expect(model.getAlternativeVersionId()).toBe(versionAfterEdit);

            await model.undo();
            expect(model.getValue()).toBe(SUPPORT_YAML);
        });
    });

    describe("variable detection and sync (#10196)", () => {
        it("shows synced, missing and unused counts and disables Sync Variables when nothing is missing", () => {
            render(renderEditor(YAML_TEMPLATE));
            mount(YAML_TEMPLATE);

            expect(screen.getByTestId("badge-synced")).toHaveTextContent("2 synced");
            expect(screen.getByTestId("badge-missing")).toHaveTextContent("0 missing");
            expect(screen.getByTestId("badge-unused")).toHaveTextContent("0 unused");
            expect(screen.getByTestId("btn-sync-variables")).toBeDisabled();
        });

        it("enables Sync Variables when the template uses an undeclared variable", () => {
            render(renderEditor(YAML_WITH_MISSING));
            mount(YAML_WITH_MISSING);

            expect(screen.getByTestId("badge-synced")).toHaveTextContent("1 synced");
            expect(screen.getByTestId("badge-missing")).toHaveTextContent("1 missing");
            expect(screen.getByTestId("btn-sync-variables")).toBeEnabled();
        });

        it.each([
            ["malformed YAML", INVALID_YAML, "badge-malformed", "Invalid document"],
            ["a document without a template field", YAML_NO_TEMPLATE, "badge-no-template", "No template field"]
        ])("disables Sync Variables for %s", (_case, source, badge, text) => {
            render(renderEditor(source));
            mount(source);

            expect(screen.getByTestId(badge)).toHaveTextContent(text);
            expect(screen.queryByTestId("badge-missing")).not.toBeInTheDocument();
            expect(screen.getByTestId("btn-sync-variables")).toBeDisabled();
        });

        it("counts the variables of the text Monaco mounts with", () => {
            render(renderEditor(YAML_NO_TEMPLATE));
            mount(YAML_WITH_MISSING);

            expect(screen.getByTestId("badge-missing")).toHaveTextContent("1 missing");
        });

        it("adds missing variables as one undoable edit and reports the new text", async () => {
            const onChange = vi.fn();
            render(renderDraft({ content: YAML_WITH_MISSING, contentType: "application/x-yaml" }, onChange));
            const { model } = mount(YAML_WITH_MISSING);
            forwardChanges(model);

            fireEvent.click(screen.getByTestId("btn-sync-variables"));

            const synced = YAML_WITH_MISSING + "  code:\n    type: string\n    description: \"\"\n";
            expect(model.getValue()).toBe(synced);
            expect(onChange).toHaveBeenCalledTimes(1);
            expect(onChange).toHaveBeenLastCalledWith(synced);
            expect(screen.getByTestId("badge-synced")).toHaveTextContent("2 synced");
            expect(screen.getByTestId("badge-missing")).toHaveTextContent("0 missing");
            expect(screen.getByTestId("btn-sync-variables")).toBeDisabled();

            await act(async () => {
                await model.undo();
            });
            expect(model.getValue()).toBe(YAML_WITH_MISSING);
            expect(onChange).toHaveBeenLastCalledWith(YAML_WITH_MISSING);
            expect(screen.getByTestId("badge-missing")).toHaveTextContent("1 missing");

            await act(async () => {
                await model.redo();
            });
            expect(model.getValue()).toBe(synced);
            expect(onChange).toHaveBeenLastCalledWith(synced);
            expect(onChange).toHaveBeenCalledTimes(3);
        });

        it("inserts only the new entry, leaving comments, blank lines and flow collections exactly as written", async () => {
            const source = SUPPORT_YAML.replace("Greet {{customer}} politely.", "Greet {{customer}} about {{topic}}.");
            const synced = source.replace("metadata:", "  topic:\n    type: string\n    description: \"\"\nmetadata:");
            const onChange = vi.fn();
            render(renderDraft({ content: source, contentType: "application/x-yaml" }, onChange));
            const { model } = mount(source);
            forwardChanges(model);
            const versionBefore = model.getAlternativeVersionId();

            fireEvent.click(screen.getByTestId("btn-sync-variables"));

            expect(model.getValue()).toBe(synced);
            expect(onChange).toHaveBeenCalledTimes(1);

            // One undo step takes the whole sync back, and one redo reapplies it.
            await act(async () => {
                await model.undo();
            });
            expect(model.getValue()).toBe(source);
            expect(model.getAlternativeVersionId()).toBe(versionBefore);
            await act(async () => {
                await model.redo();
            });
            expect(model.getValue()).toBe(synced);
        });

        it("shows a condition-only input as conditional, not missing, and leaves the text and history untouched", () => {
            const source = `templateId: conditional
template: "{{#if urgent}}Escalate. {{/if}}Hi {{name}}"
variables:
  name:
    type: string
`;
            const onChange = vi.fn();
            render(renderDraft({ content: source, contentType: "application/x-yaml" }, onChange));
            const { model } = mount(source);
            forwardChanges(model);
            const versionBefore = model.getAlternativeVersionId();

            expect(screen.getByTestId("badge-conditional")).toHaveTextContent("1 conditional");
            expect(screen.getByTestId("badge-missing")).toHaveTextContent("0 missing");
            expect(screen.getByTestId("badge-unused")).toHaveTextContent("0 unused");
            expect(screen.getByTestId("btn-sync-variables")).toBeDisabled();

            fireEvent.click(screen.getByTestId("btn-sync-variables"));

            expect(model.getValue()).toBe(source);
            expect(model.getAlternativeVersionId()).toBe(versionBefore);
            expect(onChange).not.toHaveBeenCalled();
        });

        it("explains why a list-shaped variables section cannot be synchronized and leaves the text and history untouched", () => {
            const source = `templateId: list
template: "Hello {{name}} and {{extra}}"
variables:
  - name: name
    type: string
`;
            const onChange = vi.fn();
            render(renderDraft({ content: source, contentType: "application/x-yaml" }, onChange));
            const { model } = mount(source);
            forwardChanges(model);
            const versionBefore = model.getAlternativeVersionId();

            fireEvent.click(screen.getByTestId("btn-sync-variables"));

            expect(screen.getByText("Synchronization Error")).toBeInTheDocument();
            expect(screen.getByText(/'variables' is a list\./)).toBeInTheDocument();
            expect(model.getValue()).toBe(source);
            expect(model.getAlternativeVersionId()).toBe(versionBefore);
            expect(onChange).not.toHaveBeenCalled();
        });

        it.each([
            [
                "an alias without an anchor",
                "variables: *missing",
                "The alias *missing has no anchor &missing before it. Define the anchor or remove the alias."
            ],
            [
                "a variables list containing null",
                "variables: [null]",
                "'variables' entry 1 is empty. Each entry of a 'variables' list must be a mapping with a 'name'."
            ]
        ])("explains %s without breaking the editor, then detects and syncs once it is corrected", (_case, variablesLine, error) => {
            const head = "templateId: t\ntemplate: \"Hi {{name}} {{role}}\"\n";
            const source = `${head}${variablesLine}\n`;
            const corrected = `${head}variables:\n  name:\n    type: string\n`;
            const onChange = vi.fn();
            render(renderDraft({ content: source, contentType: "application/x-yaml" }, onChange));
            const { model } = mount(source);
            forwardChanges(model);

            expect(screen.getByTestId("badge-malformed")).toHaveTextContent("Invalid document");
            expect(screen.getByTestId("detection-error").textContent).toBe(error);
            expect(screen.getByTestId("btn-sync-variables")).toBeDisabled();
            expect(model.getValue()).toBe(source);
            expect(onChange).not.toHaveBeenCalled();

            act(() => {
                typeOver(model, variablesLine, "variables:\n  name:\n    type: string");
            });

            expect(onChange).toHaveBeenLastCalledWith(corrected);
            expect(screen.queryByTestId("detection-error")).not.toBeInTheDocument();
            expect(screen.getByTestId("badge-synced")).toHaveTextContent("1 synced");
            expect(screen.getByTestId("badge-missing")).toHaveTextContent("1 missing");

            fireEvent.click(screen.getByTestId("btn-sync-variables"));

            expect(model.getValue()).toBe(`${corrected}  role:\n    type: string\n    description: ""\n`);
            expect(screen.getByTestId("badge-missing")).toHaveTextContent("0 missing");
        });

        it("reads flow-style YAML by its content type and syncs it in flow style", () => {
            const source = "{templateId: t, template: \"Hi {{name}} {{role}}\", variables: {name: {type: string}}}";
            const synced = source.replace("{name: {type: string}}", "{name: {type: string}, role: {type: string, description: \"\"}}");
            const onChange = vi.fn();
            render(renderDraft({ content: source, contentType: "application/x-yaml" }, onChange));
            const { model } = mount(source);
            forwardChanges(model);

            expect(screen.getByTestId("badge-synced")).toHaveTextContent("1 synced");
            expect(screen.getByTestId("badge-missing")).toHaveTextContent("1 missing");

            fireEvent.click(screen.getByTestId("btn-sync-variables"));

            expect(model.getValue()).toBe(synced);
            expect(onChange).toHaveBeenCalledTimes(1);
            expect(onChange).toHaveBeenLastCalledWith(synced);
        });

        it("shows an error and leaves the text unchanged when sync fails, then clears it on the next edit", () => {
            const onChange = vi.fn();
            render(renderDraft({ content: YAML_WITH_MISSING, contentType: "application/x-yaml" }, onChange));
            const { model } = mount(YAML_WITH_MISSING);
            // Change the model without an onChange, so the toolbar still offers a sync the text can't take.
            model.setValue(INVALID_YAML);

            fireEvent.click(screen.getByTestId("btn-sync-variables"));

            expect(screen.getByText("Synchronization Error")).toBeInTheDocument();
            expect(model.getValue()).toBe(INVALID_YAML);
            expect(onChange).not.toHaveBeenCalled();

            act(() => {
                capturedEditorProps.onChange(YAML_WITH_MISSING);
            });
            expect(screen.queryByText("Synchronization Error")).not.toBeInTheDocument();
        });
    });
});

describe("PromptTemplateEditor — Raw Text Preservation Pipeline", () => {
    describe("YAML content", () => {
        it("preserves full YAML artifact as raw text", () => {
            expect(contentToString(YAML_TEMPLATE)).toBe(YAML_TEMPLATE);
        });

        it("preserves YAML with conditional blocks", () => {
            expect(contentToString(YAML_WITH_CONDITIONALS)).toBe(YAML_WITH_CONDITIONALS);
        });

        it("preserves YAML with unless blocks", () => {
            expect(contentToString(YAML_WITH_UNLESS)).toBe(YAML_WITH_UNLESS);
        });

        it("preserves metadata and unrelated fields", () => {
            const result = contentToString(YAML_WITH_METADATA);
            expect(result).toBe(YAML_WITH_METADATA);
            expect(result).toContain("recommendedModels:");
            expect(result).toContain("outputSchema:");
            expect(result).toContain("mcp:");
            expect(result).toContain("tags:");
        });
    });

    describe("JSON content", () => {
        it("preserves full JSON artifact as raw text", () => {
            expect(contentToString(JSON_TEMPLATE)).toBe(JSON_TEMPLATE);
        });

        it("preserves JSON with template placeholders", () => {
            const result = contentToString(JSON_TEMPLATE);
            expect(result).toContain("{{question}}");
            expect(result).toContain("{{context}}");
        });
    });

    describe("invalid intermediate content", () => {
        it("preserves invalid YAML as-is (no re-parse/replace)", () => {
            expect(contentToString(INVALID_YAML)).toBe(INVALID_YAML);
        });

        it("preserves empty content", () => {
            expect(contentToString("")).toBe("");
        });

        it("preserves content with unclosed template tags", () => {
            const unclosed = "template: \"Hello {{name\"";
            expect(contentToString(unclosed)).toBe(unclosed);
        });

        it("preserves whitespace-only content", () => {
            expect(contentToString("   \n\n   ")).toBe("   \n\n   ");
        });

        it("preserves YAML comment-only content", () => {
            const comments = "# This is a comment\n# Another comment";
            expect(contentToString(comments)).toBe(comments);
        });
    });

    describe("content type passthrough", () => {
        it("handles object content by JSON.stringifying", () => {
            const obj = { templateId: "test", template: "hello" };
            const result = contentToString(obj);
            expect(typeof result).toBe("string");
            expect(JSON.parse(result)).toEqual(obj);
        });

        it("string content returns as-is regardless of validity", () => {
            const randomString = "not json and not valid yaml: {{{{ [[[";
            expect(contentToString(randomString)).toBe(randomString);
        });
    });
});
