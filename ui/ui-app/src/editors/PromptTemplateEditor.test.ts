// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import React from "react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { render, cleanup, act } from "@testing-library/react";
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
  author: kunal
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
