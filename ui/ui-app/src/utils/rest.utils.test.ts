import { describe, expect, it } from "vitest";
import { createEndpoint } from "./rest.utils";

describe("createEndpoint", () => {
    const baseHref = "http://localhost:8080/apis/registry/v3";

    it("should correctly substitute path parameters", () => {
        const url = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId", {
            groupId: "my-group",
            artifactId: "my-artifact"
        });
        expect(url).toBe(`${baseHref}/groups/my-group/artifacts/my-artifact`);
    });

    it("should handle paths without query parameters or path parameters", () => {
        const url = createEndpoint(baseHref, "/system/info");
        expect(url).toBe(`${baseHref}/system/info`);
    });

    it("should append query parameters correctly, including falsy boolean and numeric values", () => {
        const url = createEndpoint(
            baseHref,
            "/search",
            {},
            {
                isActive: true,
                isDeprecated: false,
                limit: 10,
                offset: 0,
                negative: -1
            }
        );
        expect(url).toBe(`${baseHref}/search?isActive=true&isDeprecated=false&limit=10&offset=0&negative=-1`);
    });

    it("should omit null, undefined, and empty string query parameters", () => {
        const url = createEndpoint(
            baseHref,
            "/search",
            {},
            {
                query: "",
                filter: null,
                sort: undefined,
                validParam: "value"
            }
        );
        expect(url).toBe(`${baseHref}/search?validParam=value`);
    });

    it("should correctly handle path and query parameters combined", () => {
        const url = createEndpoint(
            baseHref,
            "/groups/:groupId/artifacts",
            { groupId: "group-1" },
            { limit: 5, offset: 0 }
        );
        expect(url).toBe(`${baseHref}/groups/group-1/artifacts?limit=5&offset=0`);
    });
});
