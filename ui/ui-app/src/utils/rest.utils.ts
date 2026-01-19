import axios, { AxiosRequestConfig } from "axios";
import { ContentTypes } from "@models/ContentTypes.ts";
import { AuthService } from "@apicurio/common-ui-components";
import { Buffer } from "buffer";
import { AuthenticationProvider, Headers, RequestInformation, type RequestOption } from "@microsoft/kiota-abstractions";
import { ConfigService } from "@services/useConfigService";
import { RegistryClientFactory } from "@sdk/lib/sdk";
import { ApicurioRegistryClient } from "@sdk/lib/generated-client/apicurioRegistryClient.ts";
import { Labels } from "@sdk/lib/generated-client/models";
import type { Middleware } from "@microsoft/kiota-http-fetchlibrary";

const AUTH_HEADER_KEY = "Authorization";
const BEARER_TOKEN_KEY = "Bearer";

export const labelsToAny = (labels: Labels | undefined | null): any => {
    const rval: any = {
        ...(labels||{}),
        ...(labels?.additionalData||{})
    };
    delete rval["additionalData"];
    return rval;
};

/**
 * An authentication provider for Kiota - used in the generated SDK client to provide
 * auth information when making REST calls to the Registry backend.
 *
 * TODO: possibly move this to https://github.com/Apicurio/apicurio-common-ui-components
 */
export class TokenAuthenticationProvider implements AuthenticationProvider {
    private readonly key: string;
    private readonly accessTokenProvider: () => Promise<string>;
    public constructor(key: string, accessTokenProvider: () => Promise<string>) {
        this.key = key;
        this.accessTokenProvider = accessTokenProvider;
    }

    public authenticateRequest = async (request: RequestInformation, additionalAuthenticationContext?: Record<string, unknown>): Promise<void> => {
        if (!request) {
            throw new Error("request info cannot be null");
        }
        if (additionalAuthenticationContext?.claims && request.headers.has(AUTH_HEADER_KEY)) {
            request.headers.delete(AUTH_HEADER_KEY);
        }
        if (!request.headers || !request.headers.has(AUTH_HEADER_KEY)) {
            const token = await this.accessTokenProvider();
            if (!request.headers) {
                request.headers = new Headers();
            }
            if (token) {
                request.headers.add(AUTH_HEADER_KEY, `${this.key} ${token}`);
            }
        }
    };
}

export function createAuthProvider(auth: AuthService): AuthenticationProvider | undefined {
    if (auth.isOidcAuthEnabled()) {
        return new TokenAuthenticationProvider(BEARER_TOKEN_KEY, () => auth.getToken().then(v => v!));
    } else if (auth.isBasicAuthEnabled()) {
        const creds = auth.getUsernameAndPassword();
        const base64Credentials = Buffer.from(`${creds?.username}:${creds?.password}`, "ascii").toString("base64");
        return new TokenAuthenticationProvider("Basic", async () => base64Credentials);
    }
    return undefined;
}

class RefreshOn401Handler implements Middleware {
    /** Shared promise to deduplicate token refresh calls across concurrent 401 responses */
    private static refreshTokensPromise: Promise<void> | null = null;

    public next: Middleware | undefined;

    private readonly auth: AuthService;

    constructor(auth: AuthService) {
        this.auth = auth;
    }

    public async execute(
        url: string,
        requestInit: RequestInit,
        requestOptions?: Record<string, RequestOption>
    ): Promise<Response> {
        if (!this.next) {
            throw new Error("RefreshOn401Handler.next is not set");
        }

        const response = await this.next.execute(url, requestInit, requestOptions);

        // If the response is not 401, just return it
        if (response.status !== 401) {
            return response;
        }

        try {
            // Refresh tokens via the OIDC provider (using a shared promise to deduplicate calls)
            await this.refreshTokens();
        } catch (e) {
            console.error("[RefreshOn401Handler] Token refresh failed", e);
            // Refresh failed - return the original 401 response
            return response;
        }

        // Retrieve a fresh access token
        const token = await this.auth.getToken();
        if (!token) {
            console.error("[RefreshOn401Handler] No token after refresh");
            return response;
        }

        // Update Authorization header with the new token
        const headers = new globalThis.Headers(requestInit.headers);
        headers.set(AUTH_HEADER_KEY, `${BEARER_TOKEN_KEY} ${token}`);

        console.debug("[RefreshOn401Handler] Retrying request after refresh");

        // Retry request with the new token
        return await this.next.execute(url, { ...requestInit, headers }, requestOptions);
    }

    /**
     * Refreshes authentication tokens using the shared promise to avoid
     * triggering multiple parallel refresh calls
     */
    private refreshTokens(): Promise<void> {
        if (!RefreshOn401Handler.refreshTokensPromise) {
            RefreshOn401Handler.refreshTokensPromise = (async () => {
                try {
                    await this.auth.refresh();
                } finally {
                    // Always reset the shared promise so that future 401s
                    // can trigger another refresh attempt if needed
                    RefreshOn401Handler.refreshTokensPromise = null;
                }
            })();
        }

        return RefreshOn401Handler.refreshTokensPromise;
    }
}

function createRegistryClient(config: ConfigService, auth: AuthService): ApicurioRegistryClient {
    const authProvider = createAuthProvider(auth);
    const middlewares = [];
    if (auth.isOidcAuthEnabled()) {
        middlewares.push(new RefreshOn401Handler(auth));
    }

    return RegistryClientFactory.createRegistryClient(config.artifactsUrl(), authProvider, middlewares);
}

let client: ApicurioRegistryClient;

export const getRegistryClient = (config: ConfigService, auth: AuthService): ApicurioRegistryClient => {
    if (client === undefined) {
        client = createRegistryClient(config, auth);
    }
    return client;
};


const AXIOS = axios.create();

function createAxiosConfig(method: string, url: string, options: any, data?: any): AxiosRequestConfig {
    if (typeof data === "string") {
        data = new Blob([data]);
    }
    return {
        ...{
            data,
            method,
            url,
            validateStatus: (status) => {
                return status >= 200 && status < 300;
            }
        }, ...options
    };
}


function unwrapErrorData(error: any): any {
    console.debug("Error detected, unwrapping...");
    if (error && error.response && error.response.data) {
        return {
            message: error.message,
            ...error.response.data,
            status: error.response.status
        };
    } else if (error && error.response) {
        return {
            message: error.message,
            status: error.response.status
        };
    } else if (error) {
        console.error("Unknown error detected: ", error);
        return {
            message: error.message,
            status: 500
        };
    } else {
        console.error("Unknown error detected: ", error);
        return {
            message: "Unknown error",
            status: 500
        };
    }
}

/**
 * Creates an endpoint to use when making a REST call.  Supports path params and query params.
 * @param baseHref
 * @param path
 * @param params
 * @param queryParams
 */
export function createEndpoint(baseHref: string, path: string, params?: any, queryParams?: any): string {
    if (params) {
        Object.keys(params).forEach(key => {
            const value: string = encodeURIComponent(params[key]);
            path = path.replace(":" + key, value);
        });
    }
    let rval: string = createHref(baseHref, path);
    if (queryParams) {
        let first: boolean = true;
        for (const key in queryParams) {
            if (queryParams[key]) {
                const value: string = encodeURIComponent(queryParams[key]);
                if (first) {
                    rval = rval + "?" + key;
                } else {
                    rval = rval + "&" + key;
                }
                if (value !== null && value !== undefined) {
                    rval = rval + "=" + value;
                }
                first = false;
            }
        }
    }
    return rval;
}

export function createHeaders(token: string | undefined): any {
    if (token) {
        return {
            [AUTH_HEADER_KEY]: `${BEARER_TOKEN_KEY} ${token}`
        };
    } else {
        return {};
    }
}

/**
 * Creates the request options used by the HTTP service when making API calls.
 * @param headers
 */
export function createOptions(headers: { [header: string]: string }): AxiosRequestConfig {
    return { headers };
}

/**
 * Creates the request Auth options used by the HTTP service when making API calls.
 * @param auth
 */
export async function createAuthOptions(auth: AuthService): Promise<AxiosRequestConfig> {
    if (auth.isOidcAuthEnabled()) {
        const token: string | undefined = await auth.getToken();
        return createOptions(createHeaders(token));
    } else if (auth.isBasicAuthEnabled()) {
        const creds = auth.getUsernameAndPassword();
        const base64Credentials = Buffer.from(`${creds?.username}:${creds?.password}`, "ascii").toString("base64");
        const headers = { "Authorization": `Basic ${base64Credentials}` };
        return createOptions(headers);
    } else {
        return Promise.resolve({});
    }
}


/**
 * Performs an HTTP GET operation to the given URL with the given options.  Returns
 * a Promise to the HTTP response data.
 */
export function httpGet<T>(url: string, options?: AxiosRequestConfig, successCallback?: (value: any, response?: any) => T): Promise<T> {
    console.info("[BaseService] Making a GET request to: ", url);

    if (!options) {
        options = createOptions({ "Accept": ContentTypes.APPLICATION_JSON });
    }

    const config: AxiosRequestConfig = createAxiosConfig("get", url, options);
    return AXIOS.request(config)
        .then(response => {
            const data: T = response.data;
            if (successCallback) {
                return successCallback(data, response);
            } else {
                return data;
            }
        }).catch((error: any) => {
            return Promise.reject(unwrapErrorData(error));
        });
}

/**
 * Performs an HTTP POST operation to the given URL with the given body and options.  Returns
 * a Promise to null (no response data expected).
 * @param url
 * @param body
 * @param options
 * @param successCallback
 * @param progressCallback
 */
export function httpPost<I>(url: string, body: I, options?: AxiosRequestConfig, successCallback?: () => void,
                            progressCallback?: (progressEvent: any) => void): Promise<void> {
    console.info("[BaseService] Making a POST request to: ", url);

    if (!options) {
        options = createOptions({ "Content-Type": ContentTypes.APPLICATION_JSON });
    }

    const config: AxiosRequestConfig = createAxiosConfig("post", url, options, body);
    if (progressCallback) {
        const fiftyMB: number = 50 * 1024 * 1024;
        config.onUploadProgress = progressCallback;
        config.maxContentLength = fiftyMB;
        config.maxBodyLength = fiftyMB;
    }
    return AXIOS.request(config)
        .then(() => {
            if (successCallback) {
                return successCallback();
            } else {
                return;
            }
        }).catch((error: any) => {
            return Promise.reject(unwrapErrorData(error));
        });
}

export function createHref(baseHref: string, path: string): string {
    let url: string =  baseHref;
    if (url.endsWith("/")) {
        url = url.substring(0, url.length - 1);
    }
    url += path;
    return url;
}

