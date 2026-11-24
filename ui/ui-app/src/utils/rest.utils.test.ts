import { describe, expect, it, vi } from "vitest";
import { createAuthOptions, createEndpoint, httpGet, unwrapErrorData } from "./rest.utils";

describe("unwrapErrorData", () => {
    it("should correctly unwrap and spread JSON object response data", () => {
        const error = {
            message: "Request failed with status code 409",
            response: {
                status: 409,
                data: {
                    message: "Artifact already exists",
                    errorCode: 409
                }
            }
        };
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "Artifact already exists",
            errorCode: 409,
            status: 409
        });
    });

    it("should correctly handle and wrap plain-text/HTML string response data", () => {
        const error = {
            message: "Request failed with status code 504",
            response: {
                status: 504,
                data: "504 Gateway Time-out"
            }
        };
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "504 Gateway Time-out",
            status: 504
        });
    });

    it("should handle response with null/undefined data by falling back to error message", () => {
        const error = {
            message: "Request failed with status code 500",
            response: {
                status: 500,
                data: null
            }
        };
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "Request failed with status code 500",
            status: 500
        });
    });

    it("should handle errors without response object by returning status 500", () => {
        const error = new Error("Connection failed");
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "Connection failed",
            status: 500
        });
    });

    it("should handle unknown/falsy errors gracefully", () => {
        const result = unwrapErrorData(undefined);
        expect(result).toEqual({
            message: "Unknown error",
            status: 500
        });
    });

    it("should handle array response data by converting it to string message", () => {
        const error = {
            message: "Request failed with status code 400",
            response: {
                status: 400,
                data: ["Validation error 1", "Validation error 2"]
            }
        };
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "Validation error 1,Validation error 2",
            status: 400
        });
    });

    it("should handle non-string scalar response data by converting it to string message", () => {
        const error = {
            message: "Request failed with status code 500",
            response: {
                status: 500,
                data: 99999
            }
        };
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "99999",
            status: 500
        });
    });
});

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

describe("axios OIDC token refresh", () => {
    const authorization = (config: any): string | undefined => {
        const headers = config?.headers;
        return typeof headers?.get === "function" ? headers.get("Authorization") : headers?.["Authorization"];
    };

    it("refreshes the token and retries an unauthorized request once with the new token", async () => {
        const auth = {
            isOidcAuthEnabled: () => true,
            getToken: vi.fn()
                .mockResolvedValueOnce("expired-token")
                .mockResolvedValueOnce("fresh-token"),
            refresh: vi.fn().mockResolvedValue(undefined)
        } as any;

        const requests: any[] = [];
        // A custom adapter stands in for the built-in adapter, which rejects a
        // non-2xx response through axios settle() before response interceptors run.
        const adapter = vi.fn(async (config: any) => {
            requests.push(config);
            const unauthorized = requests.length === 1;
            const response = {
                data: unauthorized ? "unauthorized" : { message: "ok" },
                status: unauthorized ? 401 : 200,
                statusText: unauthorized ? "Unauthorized" : "OK",
                headers: {},
                config
            };
            if (unauthorized) {
                return Promise.reject({
                    message: "Request failed with status code 401",
                    config,
                    response
                });
            }
            return response;
        });

        const options = await createAuthOptions(auth);
        const result = await httpGet<any>("/api/test", { ...options, adapter });

        expect(adapter).toHaveBeenCalledTimes(2);
        expect(auth.refresh).toHaveBeenCalledTimes(1);
        expect(authorization(requests[0])).toBe("Bearer expired-token");
        expect(authorization(requests[1])).toBe("Bearer fresh-token");
        expect(result).toEqual({ message: "ok" });
    });
});
