import { AuthService } from "@apicurio/common-ui-components";

type ReauthenticationInterceptor = () => Promise<boolean>;
type ReauthenticationListener = (isPending: boolean) => void;

function notifyListeners(listeners: Set<ReauthenticationListener>, isPending: boolean): void {
    listeners.forEach(listener => listener(isPending));
}

/**
 * Coordinates the global re-authentication flow after a request becomes unauthorized.
 * Pages can register an interceptor to override the default redirect behavior.
 */
export interface ReauthenticationService {
    isReauthenticationPending(): boolean;
    requestReauthentication(auth: AuthService): Promise<boolean>;
    startReauthenticationRedirect(auth: AuthService): Promise<void>;
    cancelReauthentication(): void;
    registerReauthenticationInterceptor(interceptor: ReauthenticationInterceptor): () => void;
    subscribe(listener: ReauthenticationListener): () => void;
}

function createReauthenticationService(): ReauthenticationService {
    let isPending = false;
    let interceptor: ReauthenticationInterceptor | undefined;
    let requestPromise: Promise<void> | null = null;
    const listeners = new Set<ReauthenticationListener>();

    const setPending = (nextPending: boolean): void => {
        if (isPending === nextPending) {
            return;
        }

        isPending = nextPending;
        notifyListeners(listeners, isPending);
    };

    const beginOidcLoginRedirect = async (auth: AuthService): Promise<void> => {
        await auth.login("", "");
    };

    const requestReauthentication = async (auth: AuthService): Promise<boolean> => {
        if (!auth.isOidcAuthEnabled()) {
            return false;
        }

        if (isPending && requestPromise) {
            await requestPromise;
            return true;
        }

        setPending(true);
        requestPromise = (async () => {
            let intercepted = false;

            if (interceptor) {
                try {
                    intercepted = await interceptor();
                } catch (error) {
                    console.error("[ReauthenticationService] Registered interceptor failed", error);
                }
            }

            if (!intercepted) {
                try {
                    await beginOidcLoginRedirect(auth);
                } catch (error) {
                    setPending(false);
                    requestPromise = null;
                    throw error;
                }
            }
        })();

        // Keep the pending state until the flow either redirects or is explicitly dismissed.
        await requestPromise;
        return true;
    };

    const startReauthenticationRedirect = async (auth: AuthService): Promise<void> => {
        if (!auth.isOidcAuthEnabled()) {
            return;
        }

        setPending(true);
        try {
            await beginOidcLoginRedirect(auth);
        } catch (error) {
            setPending(false);
            requestPromise = null;
            throw error;
        }
    };

    const cancelReauthentication = (): void => {
        requestPromise = null;
        setPending(false);
    };

    return {
        isReauthenticationPending: () => isPending,
        requestReauthentication,
        startReauthenticationRedirect,
        cancelReauthentication,
        registerReauthenticationInterceptor: (nextInterceptor: ReauthenticationInterceptor): (() => void) => {
            interceptor = nextInterceptor;

            return () => {
                if (interceptor === nextInterceptor) {
                    interceptor = undefined;
                }
            };
        },
        subscribe: (listener: ReauthenticationListener): (() => void) => {
            listeners.add(listener);
            return () => {
                listeners.delete(listener);
            };
        }
    };
}

const REAUTHENTICATION_SERVICE = createReauthenticationService();

export function getReauthenticationService(): ReauthenticationService {
    return REAUTHENTICATION_SERVICE;
}

export const useReauthenticationService = (): ReauthenticationService => getReauthenticationService();
