import Keycloak from "keycloak-js";
import { ConfigService } from "../config";
import { Service } from "../baseService";
import { AxiosRequestConfig } from "axios";
import { LoggerService } from "../logger";
import { UsersService } from "../users";
import { User, UserManager, UserManagerSettings } from "oidc-client-ts";

const KC_CONFIG_OPTIONS: string[] = ["url", "realm", "clientId", "redirectUri"];
const KC_INIT_OPTIONS: string[] = [
    "useNonce", "adapter", "onLoad", "token", "refreshToken", "idToken", "timeSkew", "checkLoginIframe",
    "checkLoginIframeInterval", "responseMode", "redirectUri", "silentCheckSsoRedirectUri", "flow",
    "pkceMethod", "enableLogging"
];

function only(items: string[], allOptions: any): any {
    const rval: any = {};
    items.forEach(item => {
        if (allOptions[item] !== undefined) {
            rval[item] = allOptions[item];
        }
    });
    return rval;
}

export interface AuthenticatedUser {
    username: string;
    displayName: string;
    fullName: string;
    roles?: any;
}

/**
 * Initializes Keycloak instance and calls the provided callback function if successfully authenticated.
 *
 * @param onAuthenticatedCallback
 */

export class AuthService implements Service {

    protected users: UsersService = null;

    private enabled: boolean = false;
    private config: ConfigService = null;
    private logger: LoggerService = null;
    private keycloak: Keycloak.KeycloakInstance;
    private user: AuthenticatedUser;
    private userManager: UserManager;
    private oidcUser: User;

    public init = () => {
        if (this.config.authType() === "oidc") {
            this.userManager = new UserManager(this.getClientSettings());
        }
    };

    public authenticateUsingOidc = (onAuthenticatedCallback: () => void) => {
        this.userManager.getUser().then((authenticatedUser) => {
            if (authenticatedUser) {
                this.oidcUser = authenticatedUser;
                this.userManager.startSilentRenew();
                onAuthenticatedCallback();
            } else {
                console.warn("Not authenticated!");
                this.doLogin();
            }

        }).catch((error) => {
            console.log(error);
            this.user = this.fakeUser();
            onAuthenticatedCallback();
        });
    };

    public authenticateUsingKeycloak = (onAuthenticatedCallback: () => void) => {
        const configOptions: any = only(KC_CONFIG_OPTIONS, this.config.authOptions());
        const initOptions: any = only(KC_INIT_OPTIONS, this.config.authOptions());

        this.keycloak = Keycloak(configOptions);

        const addRoles: ((user: AuthenticatedUser) => void) = (user) => {
            if (this.keycloak.resourceAccess) {
                Object.keys(this.keycloak.resourceAccess)
                    .forEach(key => (user.roles = user.roles.concat(this.keycloak.resourceAccess[key].roles)));
            }

            this.logger.info("----------------");
            this.logger.info("Authenticated!  User info:", user);
            this.logger.info("----------------");
        };

        const infoToUser: (() => AuthenticatedUser) = () => {
            const ui: any = this.keycloak.userInfo;
            return {
                displayName: ui.given_name,
                fullName: ui.name,
                roles: [],
                username: ui.preferred_username
            };
        };

        this.keycloak.init(initOptions)
            .then((authenticated) => {
                if (authenticated) {
                    this.keycloak.loadUserInfo().then(() => {
                        this.user = infoToUser();
                        addRoles(this.user);
                        onAuthenticatedCallback();
                    }).catch(() => {
                        this.user = this.fakeUser();
                        addRoles(this.user);
                        onAuthenticatedCallback();
                    });
                } else {
                    console.warn("Not authenticated!");
                    this.doLogin();
                }
            });
    };

    public getClientSettings(): UserManagerSettings {
        const configOptions: any = only(KC_CONFIG_OPTIONS, this.config.authOptions());

        return {
            authority: configOptions.url,
            client_id: configOptions.clientId,
            redirect_uri: configOptions.redirectUri,
            response_type: "code",
            scope: "openid profile api1",
            filterProtocolClaims: true,
            loadUserInfo: true
        };
    }

    public fakeUser: (() => AuthenticatedUser) = () => {
        return {
            displayName: "User",
            fullName: "User",
            roles: [],
            username: "User"
        };
    };

    public oidcInfoToUser(user: User): AuthenticatedUser {
        return {
            displayName: user.profile.given_name != undefined ? user.profile.given_name : "User",
            fullName: user.profile.preferred_username != undefined ? user.profile.preferred_username : "User",
            username: user.profile.name != undefined ? user.profile.name : "User"
        };
    }

    public isAuthenticated(): boolean {
        return (this.isKeycloakAuthenticated() || this.isOidcAuthenticated());
    }

    public isKeycloakAuthenticated = () => (this.keycloak != null && this.keycloak.authenticated);

    private isOidcAuthenticated(): boolean {
        return this.userManager != null && this.oidcUser != null && !this.oidcUser.expired;
    }

    public doLogin = () => {
        if (this.config.authType() === "keycloakjs") {
            this.keycloak.login();
        } else if (this.config.authType() === "oidc") {
            this.userManager.signinRedirect().catch(reason => {
                console.log(reason);
            });
        }
    };

    public doLogout = () => {
        if (this.config.authType() === "keycloakjs") {
            this.keycloak.logout({
                redirectUri: window.location.href
            });
        } else if (this.config.authType() === "oidc") {
            this.userManager.signoutRedirect({ post_logout_redirect_uri: window.location.href });
        }
    };

    public getToken = () => this.keycloak.token;

    public getOidcToken = () => {
        return this.oidcUser.id_token;
    };

    public isAuthenticationEnabled(): boolean {
        return this.enabled;
    }

    public isRbacEnabled(): boolean {
        return this.config.authRbacEnabled();
    }

    public isObacEnabled(): boolean {
        return this.config.authObacEnabled();
    }

    public isUserAdmin(): boolean {
        if (!this.isAuthenticationEnabled()) {
            return true;
        }
        if (!this.isRbacEnabled() && !this.isObacEnabled()) {
            return true;
        }
        return this.users.currentUser().admin;
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
        if (this.isRbacEnabled() && !this.users.currentUser().developer) {
            return false;
        }
        if (this.isObacEnabled() && resourceOwner && this.users.currentUser().username !== resourceOwner) {
            return false;
        }
        return true;
    }

    public isUserId(userId: string): boolean {
        return this.users.currentUser().username === userId;
    }

    public authenticateAndRender(render: () => void): void {
        if (this.config.authType() === "keycloakjs") {
            this.enabled = true;
            this.authenticateUsingKeycloak(render);
        } else if (this.config.authType() === "oidc") {
            this.enabled = true;
            const url = new URL(window.location.href);
            if (url.searchParams.get("state")) {
                this.userManager.signinRedirectCallback().then(user => {
                    this.oidcUser = user;
                    render();
                });
            } else {
                this.authenticateUsingOidc(render);
            }
        } else {
            this.enabled = false;
            render();
        }
    }

    public getAuthInterceptor(): (config: AxiosRequestConfig) => Promise<any> {
        /* eslint-disable @typescript-eslint/no-this-alias */
        const self: AuthService = this;
        return (config: AxiosRequestConfig) => {
            if (self.config.authType() === "keycloakjs") {
                return self.updateKeycloakToken(() => {
                    config.headers.Authorization = `Bearer ${this.getToken()}`;
                    return Promise.resolve(config);
                });
            } else if (self.config.authType() === "gettoken") {
                this.logger.info("[AuthService] Using 'getToken' auth type.");
                return self.config.authGetToken()().then(token => {
                    this.logger.info("[AuthService] Token acquired.");
                    config.headers.Authorization = `Bearer ${token}`;
                    return Promise.resolve(config);
                }).catch(error => {
                    this.logger.info("[AuthService] Failed to acquire token: ", error);
                    return Promise.reject(error);
                });
            } else if (self.config.authType() === "oidc") {
                config.headers.Authorization = `Bearer ${this.getOidcToken()}`;
                return Promise.resolve(config);
            } else {
                return Promise.resolve(config);
            }
        };
    }

    private updateKeycloakToken = (successCallback) => {
        return this.keycloak.updateToken(5)
            .then(successCallback)
            .catch(this.doLogin);
    };
}
