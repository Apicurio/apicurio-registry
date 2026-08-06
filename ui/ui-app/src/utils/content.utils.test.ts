import { describe, expect, it, vi } from "vitest";

// Mock the artifact types service module: content.utils imports it only for the
// ArtifactTypes constants, but loading the real module pulls the full service (and
// PatternFly CSS) into the test runner, which node cannot parse.
vi.mock("@services/useArtifactTypesService.ts", () => ({
    ArtifactTypes: {
        PROTOBUF: "PROTOBUF"
    }
}));

import { detectVersionInContent } from "./content.utils";

const OPENAPI_JSON: string = `{
    "openapi": "3.0.2",
    "info": {
        "title": "Hello World API",
        "version": "1.0.0"
    }
}`;

const OPENAPI_YAML: string = `
openapi: 3.0.2
info:
    title: Hello World API
    version: 1.0.0
`;

const ASYNCAPI_YAML: string = `
asyncapi: 2.1.0
info:
    title: Hello world application
    version: '0.1.0'
`;

const SWAGGER_JSON: string = `{
    "swagger": "2.0",
    "info": {
        "title": "Legacy API",
        "version": "2.5"
    }
}`;

const OPENAPI_NO_VERSION: string = `{
    "openapi": "3.0.2",
    "info": {
        "title": "Hello World API"
    }
}`;

const NON_SPEC_JSON: string = `{
    "info": {
        "version": "1.0.0"
    },
    "something": "else"
}`;

describe("detectVersionInContent", () => {
    it("detects the version in an OpenAPI (JSON) spec", () => {
        expect(detectVersionInContent(OPENAPI_JSON)).toBe("1.0.0");
    });

    it("detects the version in an OpenAPI (YAML) spec", () => {
        expect(detectVersionInContent(OPENAPI_YAML)).toBe("1.0.0");
    });

    it("detects the version in an AsyncAPI (YAML) spec", () => {
        expect(detectVersionInContent(ASYNCAPI_YAML)).toBe("0.1.0");
    });

    it("detects the version in a Swagger 2.0 spec", () => {
        expect(detectVersionInContent(SWAGGER_JSON)).toBe("2.5");
    });

    it("returns undefined when the spec has no info.version", () => {
        expect(detectVersionInContent(OPENAPI_NO_VERSION)).toBeUndefined();
    });

    it("returns undefined for content that is not an API spec", () => {
        expect(detectVersionInContent(NON_SPEC_JSON)).toBeUndefined();
    });

    it("returns undefined for empty or undefined content", () => {
        expect(detectVersionInContent(undefined)).toBeUndefined();
        expect(detectVersionInContent("")).toBeUndefined();
    });

    it("returns undefined for content that cannot be parsed", () => {
        expect(detectVersionInContent("this is just some text")).toBeUndefined();
        expect(detectVersionInContent("syntax = \"proto3\";")).toBeUndefined();
    });

    it("returns undefined for a non-string version (unquoted YAML number is lossy)", () => {
        expect(detectVersionInContent("openapi: 3.0.2\ninfo:\n    version: 1.0\n")).toBeUndefined();
        expect(detectVersionInContent("{\"openapi\": \"3.0.2\", \"info\": {\"version\": {\"major\": 1}}}")).toBeUndefined();
    });

    it("returns undefined for multi-document YAML", () => {
        expect(detectVersionInContent("openapi: 3.0.2\ninfo:\n    version: '1.0.0'\n---\nsecond: doc\n")).toBeUndefined();
    });
});
