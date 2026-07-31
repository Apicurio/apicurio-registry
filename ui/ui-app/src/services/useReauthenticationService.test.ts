import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { getReauthenticationService } from "./useReauthenticationService";
import { AuthService } from "@apicurio/common-ui-components";

describe("useReauthenticationService", () => {
    const service = getReauthenticationService();
    let cleanups: Array<() => void> = [];

    // Helper mock for AuthService
    const mockAuth = {
        isOidcAuthEnabled: () => true,
        login: vi.fn().mockResolvedValue(undefined),
    } as unknown as AuthService;

    beforeEach(() => {
        service.cancelReauthentication();
        cleanups = [];
    });

    afterEach(() => {
        cleanups.forEach(fn => fn());
        cleanups = [];
        service.cancelReauthentication();
    });

    const registerInterceptor = (interceptor: any) => {
        const unregister = service.registerReauthenticationInterceptor(interceptor);
        cleanups.push(unregister);
        return unregister;
    };

    it("should initially not be pending", () => {
        expect(service.isReauthenticationPending()).toBe(false);
    });

    it("should become pending when requesting re-authentication and invoke the interceptor", async () => {
        const interceptor = vi.fn().mockResolvedValue(true);
        registerInterceptor(interceptor);

        const promise = service.requestReauthentication(mockAuth);
        expect(service.isReauthenticationPending()).toBe(true);

        const result = await promise;
        expect(result).toBe(true);
        expect(interceptor).toHaveBeenCalledTimes(1);
        expect(service.isReauthenticationPending()).toBe(true); // remains pending until explicitly dismissed
    });

    it("should cancel pending state when interceptor is unregistered while pending", async () => {
        const interceptor = vi.fn().mockResolvedValue(true);
        const unregister = registerInterceptor(interceptor);

        const promise = service.requestReauthentication(mockAuth);
        expect(service.isReauthenticationPending()).toBe(true);

        // Unregister the interceptor while re-auth is pending
        unregister();

        expect(service.isReauthenticationPending()).toBe(false);
        await promise;

        // Post-await assertion to ensure it remains false
        expect(service.isReauthenticationPending()).toBe(false);
    });

    it("should trigger redirect if no interceptor is registered", async () => {
        const redirectMockAuth = {
            isOidcAuthEnabled: () => true,
            login: vi.fn().mockResolvedValue(undefined),
        } as unknown as AuthService;

        const promise = service.requestReauthentication(redirectMockAuth);
        expect(service.isReauthenticationPending()).toBe(true);

        await promise;
        expect(redirectMockAuth.login).toHaveBeenCalledTimes(1);
    });
});
