import { beforeEach, describe, expect, it, vi } from "vitest";
import axios from "axios";
import { createAuthOptions, createEndpoint, httpGet, unwrapErrorData } from "./rest.utils";

const { requestReauthenticationMock } = vi.hoisted(() => ({
    requestReauthenticationMock: vi.fn()
}));

vi.mock("@services/useReauthenticationService.ts", () => ({
    getReauthenticationService: () => ({
        requestReauthentication: requestReauthenticationMock
    })
}));

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

    // A custom adapter has to reject non-2xx itself because the built-in adapters
    // call axios settle() before response interceptors run. This harness is used
    // for both axios instances: httpGet uses the local AXIOS instance, while the
    // data contracts and prompt rendering services call the default axios.get.
    const createAdapter = (statuses: number[]) => {
        const requests: any[] = [];
        const adapter = vi.fn(async (config: any) => {
            const index = requests.push(config) - 1;
            const status = statuses[Math.min(index, statuses.length - 1)];
            const response = {
                data: status === 200 ? { message: "ok" } : "unauthorized",
                status,
                statusText: status === 200 ? "OK" : "Unauthorized",
                headers: {},
                config
            };
            if (status !== 200) {
                return Promise.reject({
                    message: `Request failed with status code ${status}`,
                    config,
                    response
                });
            }
            return response;
        });
        return { adapter, requests };
    };

    const oidcAuth = (getToken: any, refresh: any): any => ({
        isOidcAuthEnabled: () => true,
        isBasicAuthEnabled: () => false,
        getToken,
        refresh
    });

    beforeEach(() => {
        requestReauthenticationMock.mockReset();
    });

    it("refreshes the token and retries an unauthorized request once with the new token", async () => {
        const auth = oidcAuth(
            vi.fn()
                .mockResolvedValueOnce("expired-token")
                .mockResolvedValueOnce("fresh-token"),
            vi.fn().mockResolvedValue(undefined)
        );

        const { adapter, requests } = createAdapter([401, 200]);
        const options = await createAuthOptions(auth);
        const result = await httpGet<any>("/api/test", { ...options, adapter });

        expect(adapter).toHaveBeenCalledTimes(2);
        expect(auth.refresh).toHaveBeenCalledTimes(1);
        expect(requestReauthenticationMock).not.toHaveBeenCalled();
        expect(authorization(requests[0])).toBe("Bearer expired-token");
        expect(authorization(requests[1])).toBe("Bearer fresh-token");
        expect(result).toEqual({ message: "ok" });
    });

    it("passes a 401 through unchanged when OIDC is disabled", async () => {
        const auth = {
            isOidcAuthEnabled: () => false,
            isBasicAuthEnabled: () => false,
            getToken: vi.fn(),
            refresh: vi.fn()
        } as any;

        const { adapter } = createAdapter([401]);
        const options = await createAuthOptions(auth);
        await expect(httpGet<any>("/api/test", { ...options, adapter })).rejects.toMatchObject({ status: 401 });

        expect(adapter).toHaveBeenCalledTimes(1);
        expect(auth.refresh).not.toHaveBeenCalled();
        expect(requestReauthenticationMock).not.toHaveBeenCalled();
    });

    it("starts reauthentication without retrying when the refresh rejects", async () => {
        const auth = oidcAuth(
            vi.fn().mockResolvedValue("expired-token"),
            vi.fn().mockRejectedValue(new Error("refresh failed"))
        );

        const { adapter } = createAdapter([401]);
        const options = await createAuthOptions(auth);
        await expect(httpGet<any>("/api/test", { ...options, adapter })).rejects.toMatchObject({ status: 401 });

        expect(adapter).toHaveBeenCalledTimes(1);
        expect(requestReauthenticationMock).toHaveBeenCalledTimes(1);
    });

    it("starts reauthentication without retrying when no token is returned after refresh", async () => {
        const auth = oidcAuth(
            vi.fn()
                .mockResolvedValueOnce("expired-token")
                .mockResolvedValueOnce(undefined),
            vi.fn().mockResolvedValue(undefined)
        );

        const { adapter } = createAdapter([401]);
        const options = await createAuthOptions(auth);
        await expect(httpGet<any>("/api/test", { ...options, adapter })).rejects.toMatchObject({ status: 401 });

        expect(adapter).toHaveBeenCalledTimes(1);
        expect(requestReauthenticationMock).toHaveBeenCalledTimes(1);
    });

    it("starts reauthentication after a second 401 and does not retry again", async () => {
        const auth = oidcAuth(
            vi.fn()
                .mockResolvedValueOnce("expired-token")
                .mockResolvedValueOnce("fresh-token"),
            vi.fn().mockResolvedValue(undefined)
        );

        const { adapter } = createAdapter([401, 401]);
        const options = await createAuthOptions(auth);
        await expect(httpGet<any>("/api/test", { ...options, adapter })).rejects.toMatchObject({ status: 401 });

        expect(adapter).toHaveBeenCalledTimes(2);
        expect(auth.refresh).toHaveBeenCalledTimes(1);
        expect(requestReauthenticationMock).toHaveBeenCalledTimes(1);
    });

    it("refreshes and retries on the default axios instance used by data contracts", async () => {
        const auth = oidcAuth(
            vi.fn()
                .mockResolvedValueOnce("expired-token")
                .mockResolvedValueOnce("fresh-token"),
            vi.fn().mockResolvedValue(undefined)
        );

        const { adapter, requests } = createAdapter([401, 200]);
        const options = await createAuthOptions(auth);
        const response = await axios.get<any>("/api/test", { ...options, adapter });

        expect(adapter).toHaveBeenCalledTimes(2);
        expect(auth.refresh).toHaveBeenCalledTimes(1);
        expect(requestReauthenticationMock).not.toHaveBeenCalled();
        expect(authorization(requests[0])).toBe("Bearer expired-token");
        expect(authorization(requests[1])).toBe("Bearer fresh-token");
        expect(response.data).toEqual({ message: "ok" });
    });
});
