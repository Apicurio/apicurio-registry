import { ConfigService } from "../config";
import { Service } from "../baseService";
import { AxiosRequestConfig } from "axios";
import { LoggerService } from "../logger";
import { UsersService } from "../users";
import { User, UserManager, UserManagerSettings } from "oidc-client-ts";

// export interface AuthenticatedUser {
//     username: string;
//     displayName: string;
//     fullName: string;
//     roles?: any;
// }

/**
 * Initializes Keycloak instance and calls the provided callback function if successfully authenticated.
 *
 * @param onAuthenticatedCallback
 */

export class AuthService implements Service {

    private users: UsersService | undefined;
    private config: ConfigService | undefined;
    private logger: LoggerService | undefined;

    private enabled: boolean = false;
    private userManager: UserManager | undefined;
    private oidcUser: User | undefined;

    public init = () => {
        if (this.config?.authType() === "oidc") {
            this.userManager = new UserManager(this.getClientSettings());
        }
    };

    public authenticateUsingOidc = (): Promise<any> => {
        console.debug("======> calling getUser() on user manager");
        return this.userManager?.getUser().then((authenticatedUser) => {
            console.debug("======> authenticated user: ", authenticatedUser);
            if (authenticatedUser) {
                this.oidcUser = authenticatedUser;
                this.userManager?.startSilentRenew();
                return Promise.resolve(authenticatedUser);
            } else {
                console.warn("Not authenticated, call doLogin!");
                return this.doLogin();
            }
        }) || Promise.reject(new Error("(authenticateUsingOidc) User manager is undefined."));
    };

    public getClientSettings(): UserManagerSettings {
        const configOptions: any = this.config?.authOptions();

        return {
            authority: configOptions.url,
            client_id: configOptions.clientId,
            redirect_uri: configOptions.redirectUri,
            response_type: "code",
            scope: configOptions.scopes,
            filterProtocolClaims: true,
            includeIdTokenInSilentRenew: true,
            includeIdTokenInSilentSignout: true,
            loadUserInfo: true
        };
    }

    public isAuthenticated(): boolean {
        return this.userManager != null && this.oidcUser != null && !this.oidcUser.expired;
    }

    public doLogin = (): Promise<any> => {
        console.debug("======> doLogin(), calling signinRedirect on user manager");
        return this.userManager?.signinRedirect().then(() => {
            console.debug("======> starting silent renew()");
            this.userManager?.startSilentRenew();
            console.debug("======> calling redirect callback on user manager()");
            return this.userManager?.signinRedirectCallback();
        }) || Promise.reject("(doLogin) User manager is undefined.");
    };

    public doLogout = () => {
        this.userManager?.signoutRedirect({ post_logout_redirect_uri: window.location.href });
    };

    public getOidcToken = () => {
        return this.oidcUser?.id_token;
    };

    public isAuthenticationEnabled(): boolean {
        return this.enabled;
    }

    public isRbacEnabled(): boolean {
        return this.config?.authRbacEnabled() || false;
    }

    public isObacEnabled(): boolean {
        return this.config?.authObacEnabled() || false;
    }

    public isUserAdmin(): boolean {
        if (!this.isAuthenticationEnabled()) {
            return true;
        }
        if (!this.isRbacEnabled() && !this.isObacEnabled()) {
            return true;
        }
        return this.users?.currentUser().admin || false;
    }

    public isUserDeveloper(resourceOwner?: string): boolean {
        if (!this.isAuthenticationEnabled()) {
            return true;
        }
        if (!this.isRbacEnabled() && !this.isObacEnabled()) {
            return true;
        }
        if (this.isUserAdmin()) {
            return true;
        }
        if (this.isRbacEnabled() && !this.users?.currentUser().developer) {
            return false;
        }
        if (this.isObacEnabled() && resourceOwner && this.users?.currentUser().username !== resourceOwner) {
            return false;
        }
        return true;
    }

    public isUserId(userId: string): boolean {
        return this.users?.currentUser().username === userId;
    }

    public authenticate(): Promise<any> {
        console.debug("======> authenticate()");
        if (this.config?.authType() === "oidc") {
            console.debug("======> authenticate() is oidc");
            this.enabled = true;
            const url = new URL(window.location.href);
            if (url.searchParams.get("state") || url.searchParams.get("code")) {
                console.debug("======> calling signinRedirectCallback");
                return this.userManager?.signinRedirectCallback().then(user => {
                    this.oidcUser = user;
                    return Promise.resolve(user);
                }) || Promise.reject(new Error("User manager undefined."));
            } else {
                console.debug("======> calling authenticateUsingOidc()");
                return this.authenticateUsingOidc();
            }
        } else {
            this.enabled = false;
            return Promise.resolve("Authentication not enabled.");
        }
    }

    public getAuthInterceptor(): (config: AxiosRequestConfig) => Promise<any> {
        /* eslint-disable @typescript-eslint/no-this-alias */
        const self: AuthService = this;
        return (config: AxiosRequestConfig) => {
            if (self.config?.authType() === "gettoken") {
                this.logger?.info("[AuthService] Using 'getToken' auth type.");
                return self.config.authGetToken()().then(token => {
                    this.logger?.info("[AuthService] Token acquired.");
                    if (config.headers) {
                        config.headers.Authorization = `Bearer ${token}`;
                    }
                    return Promise.resolve(config);
                }).catch(error => {
                    this.logger?.info("[AuthService] Failed to acquire token: ", error);
                    return Promise.reject(error);
                });
            } else if (self.config?.authType() === "oidc") {
                if (config.headers) {
                    config.headers.Authorization = `Bearer ${this.getOidcToken()}`;
                }
                return Promise.resolve(config);
            } else {
                return Promise.resolve(config);
            }
        };
    }
}
