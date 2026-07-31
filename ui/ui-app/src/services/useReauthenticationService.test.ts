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
        // 1. Return a never-resolving promise to simulate a truly in-flight re-authentication flow
        const pendingPromise = new Promise<boolean>(() => {});
        const firstInterceptor = vi.fn().mockReturnValue(pendingPromise);
        const unregisterFirst = registerInterceptor(firstInterceptor);

        // 2. Trigger requestReauthentication
        service.requestReauthentication(mockAuth);
        expect(service.isReauthenticationPending()).toBe(true);
        expect(firstInterceptor).toHaveBeenCalledTimes(1);

        // 3. Unregister the interceptor while the promise is still pending
        unregisterFirst();

        // Verify the pending state is cleaned up as expected
        expect(service.isReauthenticationPending()).toBe(false);

        // 4. Register a new interceptor
        const secondInterceptor = vi.fn().mockResolvedValue(true);
        registerInterceptor(secondInterceptor);

        // 5. Trigger a second requestReauthentication and assert a fresh flow starts
        const promise2 = service.requestReauthentication(mockAuth);
        expect(service.isReauthenticationPending()).toBe(true);
        expect(secondInterceptor).toHaveBeenCalledTimes(1);

        await expect(promise2).resolves.toBe(true);

        // Ensure the first interceptor was not invoked again
        expect(firstInterceptor).toHaveBeenCalledTimes(1);
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
