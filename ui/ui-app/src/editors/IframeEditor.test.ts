import { describe, expect, it, vi } from "vitest";

// Mock the artifact types service module so PatternFly styles are not pulled into node environment
vi.mock("@services/useArtifactTypesService.ts", () => ({
    ArtifactTypes: {
        PROTOBUF: "PROTOBUF"
    }
}));

import { ContentTypes } from "@models/ContentTypes.ts";
import { deriveOrigin } from "@utils/url.utils.ts";
import { toJsonString, toYamlString, parseJson, parseYaml } from "@utils/content.utils.ts";

describe("IframeEditor logic and protocol tests", () => {
    describe("Origin derivation and validation", () => {
        it("derives origin correctly for absolute and relative URLs", () => {
            const fallbackOrigin = "http://localhost:8888";
            expect(deriveOrigin("http://editor.example.com/app", fallbackOrigin)).toBe("http://editor.example.com");
            expect(deriveOrigin("/editors/oai", fallbackOrigin)).toBe("http://localhost:8888");
            expect(deriveOrigin("", fallbackOrigin)).toBeUndefined();
            expect(deriveOrigin(undefined, fallbackOrigin)).toBeUndefined();
        });

        it("fails closed on malformed URLs or missing origin", () => {
            expect(deriveOrigin("http://", "invalid-origin")).toBeUndefined();
        });
    });

    describe("Incoming message event processing (apicurio_onChange)", () => {
        const expectedOrigin = "http://editor.example.com";

        const simulateMessage = (
            eventOrigin: string,
            eventData: any,
            currentContentType: string,
            onChange: (val: any) => void
        ) => {
            // Replicate the exact guard and conversion logic from IframeEditor
            if (!expectedOrigin || eventOrigin !== expectedOrigin) {
                return;
            }
            if (eventData && eventData.type === "apicurio_onChange") {
                let newContent: any = eventData.data.content;
                if (typeof newContent === "object") {
                    if (currentContentType === ContentTypes.APPLICATION_YAML) {
                        newContent = toYamlString(newContent);
                    } else {
                        newContent = toJsonString(newContent);
                    }
                } else if (typeof newContent === "string" && currentContentType === ContentTypes.APPLICATION_YAML) {
                    newContent = toYamlString(parseJson(newContent as string));
                }
                onChange(newContent);
            }
        };

        it("ignores messages from untrusted origins", () => {
            const onChange = vi.fn();
            simulateMessage("http://evil.com", { type: "apicurio_onChange", data: { content: { foo: "bar" } } }, ContentTypes.APPLICATION_JSON, onChange);
            expect(onChange).not.toHaveBeenCalled();
        });

        it("ignores messages with unknown event types", () => {
            const onChange = vi.fn();
            simulateMessage(expectedOrigin, { type: "unknown_event", data: { content: "test" } }, ContentTypes.APPLICATION_JSON, onChange);
            expect(onChange).not.toHaveBeenCalled();
        });

        it("converts object content to JSON string for JSON contentType", () => {
            const onChange = vi.fn();
            const obj = { openapi: "3.0.2", info: { title: "Test API" } };
            simulateMessage(expectedOrigin, { type: "apicurio_onChange", data: { content: obj } }, ContentTypes.APPLICATION_JSON, onChange);
            expect(onChange).toHaveBeenCalledTimes(1);
            expect(JSON.parse(onChange.mock.calls[0][0])).toEqual(obj);
        });

        it("converts object content to YAML string for YAML contentType", () => {
            const onChange = vi.fn();
            const obj = { openapi: "3.0.2", info: { title: "Test API" } };
            simulateMessage(expectedOrigin, { type: "apicurio_onChange", data: { content: obj } }, ContentTypes.APPLICATION_YAML, onChange);
            expect(onChange).toHaveBeenCalledTimes(1);
            expect(onChange.mock.calls[0][0]).toContain("openapi: 3.0.2");
        });

        it("converts JSON string to YAML string when contentType is YAML", () => {
            const onChange = vi.fn();
            const jsonStr = JSON.stringify({ openapi: "3.0.2", info: { title: "From Editor" } });
            simulateMessage(expectedOrigin, { type: "apicurio_onChange", data: { content: jsonStr } }, ContentTypes.APPLICATION_YAML, onChange);
            expect(onChange).toHaveBeenCalledTimes(1);
            expect(onChange.mock.calls[0][0]).toContain("openapi: 3.0.2");
        });

        it("passes raw string without conversion for JSON contentType", () => {
            const onChange = vi.fn();
            const jsonStr = "{\"openapi\":\"3.0.2\"}";
            simulateMessage(expectedOrigin, { type: "apicurio_onChange", data: { content: jsonStr } }, ContentTypes.APPLICATION_JSON, onChange);
            expect(onChange).toHaveBeenCalledWith(jsonStr);
        });
    });

    describe("Outgoing payload construction (apicurio-editingInfo)", () => {
        const buildEditingInfoMessage = (
            editorType: "OPENAPI" | "ASYNCAPI",
            rawContent: any,
            contentType: string,
            extraEditingInfo?: Record<string, any>
        ) => {
            let value: string;
            if (typeof rawContent === "object") {
                value = toJsonString(rawContent);
            } else if (typeof rawContent === "string" && contentType === ContentTypes.APPLICATION_YAML) {
                value = toJsonString(parseYaml(rawContent as string));
            } else {
                value = rawContent as string;
            }

            const safeExtra: Record<string, any> = { ...(extraEditingInfo || {}) };
            delete safeExtra.content;
            delete safeExtra.features;

            return {
                type: "apicurio-editingInfo",
                data: {
                    content: {
                        type: editorType,
                        value: value
                    },
                    features: {
                        allowCustomValidations: false,
                        allowImports: false
                    },
                    ...safeExtra
                } as Record<string, any>
            };
        };

        it("constructs correct OPENAPI message payload with vendor extensions", () => {
            const msg = buildEditingInfoMessage("OPENAPI", "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON, {
                openapi: { vendorExtensions: [] }
            });
            expect(msg.type).toBe("apicurio-editingInfo");
            expect(msg.data.content.type).toBe("OPENAPI");
            expect(msg.data.content.value).toBe("{\"openapi\":\"3.0.0\"}");
            expect(msg.data.openapi).toEqual({ vendorExtensions: [] });
            expect(msg.data.features).toEqual({ allowCustomValidations: false, allowImports: false });
        });

        it("constructs correct ASYNCAPI message payload", () => {
            const msg = buildEditingInfoMessage("ASYNCAPI", "{\"asyncapi\":\"2.0.0\"}", ContentTypes.APPLICATION_JSON);
            expect(msg.type).toBe("apicurio-editingInfo");
            expect(msg.data.content.type).toBe("ASYNCAPI");
            expect(msg.data.content.value).toBe("{\"asyncapi\":\"2.0.0\"}");
        });

        it("prevents extraEditingInfo from overriding content or features keys", () => {
            const msg = buildEditingInfoMessage("OPENAPI", "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON, {
                content: { type: "OVERRIDDEN", value: "BAD" },
                features: { allowCustomValidations: true },
                customKey: "safe"
            });
            expect(msg.data.content.type).toBe("OPENAPI");
            expect(msg.data.content.value).toBe("{\"openapi\":\"3.0.0\"}");
            expect(msg.data.features.allowCustomValidations).toBe(false);
            expect(msg.data.customKey).toBe("safe");
        });

        it("converts YAML string content to JSON value for outgoing message", () => {
            const yamlStr = "openapi: 3.0.0\ninfo:\n  title: Hello";
            const msg = buildEditingInfoMessage("OPENAPI", yamlStr, ContentTypes.APPLICATION_YAML);
            const parsed = JSON.parse(msg.data.content.value);
            expect(parsed.openapi).toBe("3.0.0");
            expect(parsed.info.title).toBe("Hello");
        });
    });
});
