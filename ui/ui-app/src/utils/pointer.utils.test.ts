import { describe, expect, it } from "vitest";
import YAML from "yaml";
import { locatePointer, parsePointer } from "./pointer.utils";

const SCHEMA = {
    type: "object",
    properties: {
        name: { type: "string", maxLength: 10 },
        "a/b": { type: "integer" },
        tags: { type: "array", items: [{ type: "string" }, { type: "number" }] }
    }
};

// The same two renderings the Content tab produces (ContentTabContent.tsx).
const AS_JSON: string = JSON.stringify(SCHEMA, null, 2);
const AS_YAML: string = YAML.stringify(SCHEMA, null, 4);

const textAt = (text: string, pointer: string): string => {
    const location = locatePointer(text, pointer)!;
    const lines: string[] = text.split("\n");
    const { startLine, startColumn, endLine, endColumn } = location.range;
    if (startLine === endLine) {
        return lines[startLine - 1].substring(startColumn - 1, endColumn - 1);
    }
    return [
        lines[startLine - 1].substring(startColumn - 1),
        ...lines.slice(startLine, endLine - 1),
        lines[endLine - 1].substring(0, endColumn - 1)
    ].join("\n");
};

describe("parsePointer", () => {
    it("splits and unescapes tokens", () => {
        expect(parsePointer("/properties/a~1b/x~0y")).toEqual(["properties", "a/b", "x~y"]);
    });

    it("treats the empty string as the root", () => {
        expect(parsePointer("")).toEqual([]);
    });

    it("accepts the URI fragment form", () => {
        expect(parsePointer("#/properties/a%20b")).toEqual(["properties", "a b"]);
    });

    it("rejects a value that is not a pointer", () => {
        expect(parsePointer("properties/name")).toBeUndefined();
    });
});

describe("locatePointer", () => {
    it("finds a nested keyword in pretty-printed JSON", () => {
        const location = locatePointer(AS_JSON, "/properties/name/maxLength")!;
        expect(location.exact).toBe(true);
        expect(location.range).toEqual({ startLine: 6, startColumn: 7, endLine: 6, endColumn: 22 });
        expect(textAt(AS_JSON, "/properties/name/maxLength")).toBe("\"maxLength\": 10");
    });

    it("finds the same keyword in the YAML rendering", () => {
        const location = locatePointer(AS_YAML, "/properties/name/maxLength")!;
        expect(location.exact).toBe(true);
        expect(textAt(AS_YAML, "/properties/name/maxLength")).toBe("maxLength: 10");
        expect(location.range.startLine).toBe(5);
    });

    it("resolves escaped tokens", () => {
        expect(textAt(AS_JSON, "/properties/a~1b/type")).toBe("\"type\": \"integer\"");
    });

    it("resolves array indexes", () => {
        expect(textAt(AS_YAML, "/properties/tags/items/1/type")).toBe("type: number");
    });

    it("covers a whole member when it points at an object", () => {
        expect(textAt(AS_YAML, "/properties/name")).toBe("name:\n        type: string\n        maxLength: 10");
    });

    it("falls back to the deepest part that resolves", () => {
        const location = locatePointer(AS_JSON, "/properties/name/minLength")!;
        expect(location.exact).toBe(false);
        expect(location.resolvedPointer).toBe("/properties/name");
        expect(location.range.startLine).toBe(4);
    });

    it("does not index into an object with a number", () => {
        const location = locatePointer(AS_JSON, "/properties/0")!;
        expect(location.exact).toBe(false);
        expect(location.resolvedPointer).toBe("/properties");
    });

    it("returns the whole document for the root pointer", () => {
        const location = locatePointer(AS_JSON, "")!;
        expect(location.exact).toBe(true);
        expect(location.range.startLine).toBe(1);
        expect(location.range.endLine).toBe(AS_JSON.split("\n").length);
    });

    it("returns undefined for text that cannot be parsed", () => {
        expect(locatePointer("{ \"a\": ", "/a")).toBeUndefined();
    });

    it("returns undefined for a value that is not a pointer", () => {
        expect(locatePointer(AS_JSON, "properties.name")).toBeUndefined();
    });
});

// Real checker output, captured from Avro 1.12 SchemaCompatibility and from the JSON Schema
// checker's tests (ApitomyJsonSchemaCompatibilityChecker reports JSON Pointers).
describe("locatePointer with real checker contexts", () => {
    const AVRO_READER = JSON.stringify({
        type: "record",
        name: "U",
        fields: [
            { name: "id", type: "string" },
            { name: "color", type: { type: "enum", name: "C", symbols: ["R", "G"] } },
            { name: "n", type: "int" }
        ]
    }, null, 2);

    it("maps an Avro TYPE_MISMATCH location", () => {
        expect(textAt(AVRO_READER, "/fields/0/type")).toBe("\"type\": \"string\"");
    });

    it("maps an Avro MISSING_ENUM_SYMBOLS location", () => {
        const location = locatePointer(AVRO_READER, "/fields/1/type/symbols")!;
        expect(location.exact).toBe(true);
        expect(textAt(AVRO_READER, "/fields/1/type/symbols")).toBe("\"symbols\": [\n          \"R\",\n          \"G\"\n        ]");
    });

    it("maps an Avro READER_FIELD_MISSING_DEFAULT_VALUE location to the whole field", () => {
        expect(textAt(AVRO_READER, "/fields/2")).toBe("{\n      \"name\": \"n\",\n      \"type\": \"int\"\n    }");
    });

    it("maps a JSON Schema context from the Apitomy checker", () => {
        const schema = JSON.stringify({ type: "object", properties: { age: { type: "integer" } } }, null, 2);
        expect(textAt(schema, "/properties/age")).toBe("\"age\": {\n      \"type\": \"integer\"\n    }");
    });

    it("does not locate an OpenAPI URL path, which is not a pointer into the document", () => {
        // OpenApiCompatibilityChecker reports endpoint paths such as "/pets/{id}". Nothing below
        // the root resolves, so callers must treat an empty resolvedPointer as "not located".
        const openapi = JSON.stringify({ openapi: "3.0.2", paths: { "/pets/{id}": {} } }, null, 2);
        const location = locatePointer(openapi, "/pets/{id}")!;
        expect(location.exact).toBe(false);
        expect(location.resolvedPointer).toBe("");
    });

    it("does not locate a Protobuf message name", () => {
        const location = locatePointer(AS_JSON, "/MessageName")!;
        expect(location.exact).toBe(false);
        expect(location.resolvedPointer).toBe("");
    });
});
