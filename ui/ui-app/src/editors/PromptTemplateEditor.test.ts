// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import React from "react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { render, cleanup, act } from "@testing-library/react";

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

import { PromptTemplateEditor } from "./PromptTemplateEditor.tsx";
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

describe("PromptTemplateEditor — Mounted Component Lifecycle & Synchronization", () => {
    beforeEach(() => {
        capturedEditorProps = null;
        registerPromptTemplate(monaco);
    });

    afterEach(() => {
        cleanup();
    });

    it("executes full mount lifecycle: initial content, props forwarding, onMount, and no spurious setValue", () => {
        const mockOnChange = vi.fn();
        const mockEditor = {
            getValue: vi.fn().mockReturnValue(YAML_TEMPLATE),
            setValue: vi.fn()
        };

        render(
            React.createElement(PromptTemplateEditor, {
                content: { content: YAML_TEMPLATE, contentType: "application/x-yaml" } as any,
                onChange: mockOnChange
            })
        );

        // 1. Initial mounted state
        expect(capturedEditorProps).not.toBeNull();
        expect(capturedEditorProps.defaultLanguage).toBe("prompt-template");
        expect(capturedEditorProps.defaultValue).toBe(YAML_TEMPLATE);

        // Mount the Monaco editor instance
        act(() => {
            capturedEditorProps.onMount(mockEditor);
        });

        // 5. setValue was NOT called on initial mount
        expect(mockEditor.setValue).not.toHaveBeenCalled();
    });

    it("does not call setValue or reset editor when rerendered with unchanged content", () => {
        const mockOnChange = vi.fn();
        const mockEditor = {
            getValue: vi.fn().mockReturnValue(YAML_TEMPLATE),
            setValue: vi.fn()
        };

        const { rerender } = render(
            React.createElement(PromptTemplateEditor, {
                content: { content: YAML_TEMPLATE, contentType: "application/x-yaml" } as any,
                onChange: mockOnChange
            })
        );

        act(() => {
            capturedEditorProps.onMount(mockEditor);
        });

        // 2. Rerender with unchanged content (new object reference, identical content)
        act(() => {
            rerender(
                React.createElement(PromptTemplateEditor, {
                    content: { content: YAML_TEMPLATE, contentType: "application/x-yaml" } as any,
                    onChange: mockOnChange
                })
            );
        });

        // 6. Verify unchanged content does not unnecessarily reset the editor
        expect(mockEditor.setValue).not.toHaveBeenCalled();
    });

    it("synchronizes external content update via setValue and updates editor content", () => {
        const mockOnChange = vi.fn();
        let editorContent = YAML_TEMPLATE;
        const mockEditor = {
            getValue: vi.fn().mockImplementation(() => editorContent),
            setValue: vi.fn().mockImplementation((val: string) => {
                editorContent = val;
            })
        };

        const { rerender } = render(
            React.createElement(PromptTemplateEditor, {
                content: { content: YAML_TEMPLATE, contentType: "application/x-yaml" } as any,
                onChange: mockOnChange
            })
        );

        act(() => {
            capturedEditorProps.onMount(mockEditor);
        });

        // 3. Replace content through an external update / navigation / recovery update
        const externalUpdated = `templateId: external-update
name: External Update
template: "Hello {{audience}}!"`;

        act(() => {
            rerender(
                React.createElement(PromptTemplateEditor, {
                    content: { content: externalUpdated, contentType: "application/x-yaml" } as any,
                    onChange: mockOnChange
                })
            );
        });

        // 5. Verify setValue was called when expected
        expect(mockEditor.setValue).toHaveBeenCalledTimes(1);
        expect(mockEditor.setValue).toHaveBeenCalledWith(externalUpdated);

        // 4. Verify the resulting editor model/content
        expect(mockEditor.getValue()).toBe(externalUpdated);
    });

    it("passes malformed intermediate input directly to props.onChange unchanged without throwing", () => {
        const mockOnChange = vi.fn();
        render(
            React.createElement(PromptTemplateEditor, {
                content: { content: INVALID_YAML, contentType: "application/x-yaml" } as any,
                onChange: mockOnChange
            })
        );

        // 7. Verify malformed intermediate input is passed to the parent unchanged
        const midEditBroken = INVALID_YAML + "\n  incomplete_placeholder: {{broken";
        act(() => {
            expect(() => capturedEditorProps.onChange(midEditBroken)).not.toThrow();
        });

        expect(mockOnChange).toHaveBeenCalledTimes(1);
        expect(mockOnChange).toHaveBeenCalledWith(midEditBroken);
    });

    it("recovers highlighting after incomplete intermediate input becomes valid using the real tokenizer", () => {
        // 8. Verify highlighting recovers after incomplete input becomes valid using the real tokenizer/editor path
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
