import React from "react";
import ReactDOMServer from "react-dom/server";
import { describe, expect, it, vi, beforeEach } from "vitest";

// Mock the artifact types service module: content.utils imports it only for the
// ArtifactTypes constants, but loading the real module pulls PatternFly CSS into the test runner.
vi.mock("@services/useArtifactTypesService.ts", () => ({
    ArtifactTypes: {
        PROTOBUF: "PROTOBUF",
        PROMPT_TEMPLATE: "PROMPT_TEMPLATE"
    }
}));

// Mock RegistryCodeEditor to test props forwarding without loading Monaco runtime.
let capturedEditorProps: any = null;
vi.mock("@app/components/codeEditor/RegistryEditors.tsx", () => ({
    RegistryCodeEditor: (props: any) => {
        capturedEditorProps = props;
        return React.createElement("div", { "data-testid": "mocked-registry-editor" });
    }
}));

import { PromptTemplateEditor } from "./PromptTemplateEditor.tsx";
import { contentToString } from "@utils/content.utils.ts";

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

describe("PromptTemplateEditor — Component Integration Tests", () => {
    beforeEach(() => {
        capturedEditorProps = null;
    });

    it("configures RegistryCodeEditor with defaultLanguage='prompt-template'", () => {
        const mockOnChange = vi.fn();
        ReactDOMServer.renderToStaticMarkup(
            React.createElement(PromptTemplateEditor, {
                content: { content: YAML_TEMPLATE, contentType: "application/x-yaml" } as any,
                onChange: mockOnChange
            })
        );

        expect(capturedEditorProps).not.toBeNull();
        expect(capturedEditorProps.defaultLanguage).toBe("prompt-template");
    });

    it("passes initial raw artifact string to editor defaultValue unchanged", () => {
        const mockOnChange = vi.fn();
        ReactDOMServer.renderToStaticMarkup(
            React.createElement(PromptTemplateEditor, {
                content: { content: YAML_TEMPLATE, contentType: "application/x-yaml" } as any,
                onChange: mockOnChange
            })
        );

        expect(capturedEditorProps.defaultValue).toBe(YAML_TEMPLATE);
    });

    it("passes initial JSON artifact string to editor defaultValue unchanged", () => {
        const mockOnChange = vi.fn();
        ReactDOMServer.renderToStaticMarkup(
            React.createElement(PromptTemplateEditor, {
                content: { content: JSON_TEMPLATE, contentType: "application/json" } as any,
                onChange: mockOnChange
            })
        );

        expect(capturedEditorProps.defaultValue).toBe(JSON_TEMPLATE);
    });

    it("forwards editor onChange directly to props.onChange with raw content", () => {
        const mockOnChange = vi.fn();
        ReactDOMServer.renderToStaticMarkup(
            React.createElement(PromptTemplateEditor, {
                content: { content: YAML_TEMPLATE, contentType: "application/x-yaml" } as any,
                onChange: mockOnChange
            })
        );

        expect(capturedEditorProps.onChange).toBe(mockOnChange);

        const updatedContent = "templateId: updated\ntemplate: 'Hello {{user}}'";
        capturedEditorProps.onChange(updatedContent);
        expect(mockOnChange).toHaveBeenCalledTimes(1);
        expect(mockOnChange).toHaveBeenCalledWith(updatedContent);
    });

    it("forwards invalid intermediate YAML through onChange without parsing/throwing", () => {
        const mockOnChange = vi.fn();
        ReactDOMServer.renderToStaticMarkup(
            React.createElement(PromptTemplateEditor, {
                content: { content: INVALID_YAML, contentType: "application/x-yaml" } as any,
                onChange: mockOnChange
            })
        );

        expect(capturedEditorProps.defaultValue).toBe(INVALID_YAML);

        const midEditContent = INVALID_YAML + "\n  extra broken line: {{";
        expect(() => capturedEditorProps.onChange(midEditContent)).not.toThrow();
        expect(mockOnChange).toHaveBeenCalledWith(midEditContent);
    });

    it("stores editor reference onMount", () => {
        ReactDOMServer.renderToStaticMarkup(
            React.createElement(PromptTemplateEditor, {
                content: { content: YAML_TEMPLATE, contentType: "application/x-yaml" } as any,
                onChange: vi.fn()
            })
        );

        const mockStandaloneEditor = {
            getValue: vi.fn().mockReturnValue(YAML_TEMPLATE),
            setValue: vi.fn()
        };

        expect(() => capturedEditorProps.onMount(mockStandaloneEditor)).not.toThrow();
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
