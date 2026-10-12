import { describe, expect, it, vi } from "vitest";

// Mock modules that pull PatternFly styles / browser-only services into the node test environment.
vi.mock("@services/useArtifactTypesService.ts", () => ({
    ArtifactTypes: {
        PROTOBUF: "PROTOBUF"
    }
}));

import { ContentTypes } from "@models/ContentTypes.ts";
import { DraftContent } from "@models/drafts";
import { parseYaml } from "@utils/content.utils.ts";
import { buildEditingInfoMessage, createEditorMessageListener } from "./IframeEditorProtocol";

const EDITOR_ORIGIN = "http://editor.example.com";

const messageEvent = (origin: string, data: any): MessageEvent => {
    return { origin, data } as MessageEvent;
};

const draft = (content: any, contentType: string): DraftContent => {
    return { content, contentType } as DraftContent;
};

describe("createEditorMessageListener", () => {
    const newListener = (expectedOrigin: string | undefined, contentType: string, onChange: (v: any) => void) => {
        return createEditorMessageListener(expectedOrigin, "TestEditor", () => contentType, () => onChange);
    };

    it("ignores messages from an untrusted origin", () => {
        const onChange = vi.fn();
        const listener = newListener(EDITOR_ORIGIN, ContentTypes.APPLICATION_JSON, onChange);

        listener(messageEvent("http://evil.com", { type: "apicurio_onChange", data: { content: "{}" } }));

        expect(onChange).not.toHaveBeenCalled();
    });

    it("fails closed when the expected origin is unknown", () => {
        const onChange = vi.fn();
        const listener = newListener(undefined, ContentTypes.APPLICATION_JSON, onChange);

        listener(messageEvent(EDITOR_ORIGIN, { type: "apicurio_onChange", data: { content: "{}" } }));

        expect(onChange).not.toHaveBeenCalled();
    });

    it("ignores messages with an unknown type or no data", () => {
        const onChange = vi.fn();
        const listener = newListener(EDITOR_ORIGIN, ContentTypes.APPLICATION_JSON, onChange);

        listener(messageEvent(EDITOR_ORIGIN, { type: "unknown_event", data: { content: "{}" } }));
        listener(messageEvent(EDITOR_ORIGIN, null));

        expect(onChange).not.toHaveBeenCalled();
    });

    it("converts object content to a JSON string for JSON drafts", () => {
        const onChange = vi.fn();
        const listener = newListener(EDITOR_ORIGIN, ContentTypes.APPLICATION_JSON, onChange);
        const obj = { openapi: "3.0.2", info: { title: "Test API" } };

        listener(messageEvent(EDITOR_ORIGIN, { type: "apicurio_onChange", data: { content: obj } }));

        expect(onChange).toHaveBeenCalledTimes(1);
        expect(typeof onChange.mock.calls[0][0]).toBe("string");
        expect(JSON.parse(onChange.mock.calls[0][0])).toEqual(obj);
    });

    it("converts object content to a YAML string for YAML drafts", () => {
        const onChange = vi.fn();
        const listener = newListener(EDITOR_ORIGIN, ContentTypes.APPLICATION_YAML, onChange);
        const obj = { openapi: "3.0.2", info: { title: "Test API" } };

        listener(messageEvent(EDITOR_ORIGIN, { type: "apicurio_onChange", data: { content: obj } }));

        expect(onChange).toHaveBeenCalledTimes(1);
        expect(parseYaml(onChange.mock.calls[0][0])).toEqual(obj);
    });

    it("converts a JSON string to a YAML string for YAML drafts", () => {
        const onChange = vi.fn();
        const listener = newListener(EDITOR_ORIGIN, ContentTypes.APPLICATION_YAML, onChange);
        const obj = { asyncapi: "2.0.0", info: { title: "From Editor" } };

        listener(messageEvent(EDITOR_ORIGIN, { type: "apicurio_onChange", data: { content: JSON.stringify(obj) } }));

        expect(onChange).toHaveBeenCalledTimes(1);
        expect(onChange.mock.calls[0][0]).toContain("asyncapi: 2.0.0");
        expect(parseYaml(onChange.mock.calls[0][0])).toEqual(obj);
    });

    it("passes a string through unchanged for JSON drafts", () => {
        const onChange = vi.fn();
        const listener = newListener(EDITOR_ORIGIN, ContentTypes.APPLICATION_JSON, onChange);
        const jsonStr = "{\"openapi\":\"3.0.2\"}";

        listener(messageEvent(EDITOR_ORIGIN, { type: "apicurio_onChange", data: { content: jsonStr } }));

        expect(onChange).toHaveBeenCalledExactlyOnceWith(jsonStr);
    });

    it("reads the latest content type and onChange on every message", () => {
        let contentType: string = ContentTypes.APPLICATION_JSON;
        const firstOnChange = vi.fn();
        const secondOnChange = vi.fn();
        let onChange = firstOnChange;
        const listener = createEditorMessageListener(EDITOR_ORIGIN, "TestEditor", () => contentType, () => onChange);
        const obj = { openapi: "3.0.2" };

        listener(messageEvent(EDITOR_ORIGIN, { type: "apicurio_onChange", data: { content: obj } }));
        contentType = ContentTypes.APPLICATION_YAML;
        onChange = secondOnChange;
        listener(messageEvent(EDITOR_ORIGIN, { type: "apicurio_onChange", data: { content: obj } }));

        expect(firstOnChange).toHaveBeenCalledTimes(1);
        expect(JSON.parse(firstOnChange.mock.calls[0][0])).toEqual(obj);
        expect(secondOnChange).toHaveBeenCalledTimes(1);
        expect(secondOnChange.mock.calls[0][0]).toContain("openapi: 3.0.2");
    });
});

describe("buildEditingInfoMessage", () => {
    it("builds an OPENAPI message including extra editing info", () => {
        const msg = buildEditingInfoMessage("OPENAPI", "OpenApiEditor",
            draft("{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON), { openapi: { vendorExtensions: [] } });

        expect(msg).toEqual({
            type: "apicurio-editingInfo",
            data: {
                content: { type: "OPENAPI", value: "{\"openapi\":\"3.0.0\"}" },
                features: { allowCustomValidations: false, allowImports: false },
                openapi: { vendorExtensions: [] }
            }
        });
    });

    it("builds an ASYNCAPI message with no extra editing info", () => {
        const msg = buildEditingInfoMessage("ASYNCAPI", "AsyncApiEditor",
            draft("{\"asyncapi\":\"2.0.0\"}", ContentTypes.APPLICATION_JSON));

        expect(msg).toEqual({
            type: "apicurio-editingInfo",
            data: {
                content: { type: "ASYNCAPI", value: "{\"asyncapi\":\"2.0.0\"}" },
                features: { allowCustomValidations: false, allowImports: false }
            }
        });
    });

    it("converts YAML string content to a JSON value", () => {
        const msg = buildEditingInfoMessage("OPENAPI", "OpenApiEditor",
            draft("openapi: 3.0.0\ninfo:\n  title: Hello", ContentTypes.APPLICATION_YAML));

        expect(JSON.parse(msg.data.content.value)).toEqual({ openapi: "3.0.0", info: { title: "Hello" } });
    });

    it("converts object content to a JSON value", () => {
        const msg = buildEditingInfoMessage("ASYNCAPI", "AsyncApiEditor",
            draft({ asyncapi: "2.6.0" }, ContentTypes.APPLICATION_JSON));

        expect(JSON.parse(msg.data.content.value)).toEqual({ asyncapi: "2.6.0" });
    });

    it("does not allow extra editing info to override content or features", () => {
        const extra: any = {
            content: { type: "OVERRIDDEN", value: "BAD" },
            features: { allowCustomValidations: true, allowImports: true },
            customKey: "safe"
        };
        const msg = buildEditingInfoMessage("OPENAPI", "OpenApiEditor",
            draft("{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON), extra);

        expect(msg.data.content).toEqual({ type: "OPENAPI", value: "{\"openapi\":\"3.0.0\"}" });
        expect(msg.data.features).toEqual({ allowCustomValidations: false, allowImports: false });
        expect(msg.data.customKey).toBe("safe");
    });
});
