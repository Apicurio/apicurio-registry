import Keycloak from "keycloak-js";
import {ConfigService} from "../config";
import {Service} from "../baseService";
import {AxiosRequestConfig} from "axios";

const KC_CONFIG_OPTIONS: string[] = ["url", "realm", "clientId"];
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


/**
 * Initializes Keycloak instance and calls the provided callback function if successfully authenticated.
 *
 * @param onAuthenticatedCallback
 */

export class AuthService implements Service {

    private config: ConfigService = null;
    private keycloak: Keycloak.KeycloakInstance;

    public init = () => {
        // no init?
    }

    public authenticateUsingKeycloak = (onAuthenticatedCallback: () => void) => {
        const configOptions: any = only(KC_CONFIG_OPTIONS, this.config.authOptions());
        const initOptions: any = only(KC_INIT_OPTIONS, this.config.authOptions());

        this.keycloak = Keycloak(configOptions)

        this.keycloak.init(initOptions)
            .then((authenticated) => {
                if (authenticated) {
                    onAuthenticatedCallback();
                } else {
                    console.warn("Not authenticated!");
                    this.doLogin();
                }
            })
    };

    public doLogin = () => this.keycloak.login;

    public doLogout = () =>  this.keycloak.logout;

    public getToken = () => this.keycloak.token;

    public authenticateAndRender(render: () => void): void {
        if (this.config.authType() === "keycloakjs") {
            this.authenticateUsingKeycloak(render);
        } else {
            render();
        }
    }

    public getAuthInterceptor(): (config: AxiosRequestConfig) => Promise<any> {
        const self: AuthService = this;
        const interceptor = (config: AxiosRequestConfig) => {
            if (self.config.authType() === "keycloakjs") {
                return self.updateKeycloakToken(() => {
                    config.headers.Authorization = `Bearer ${this.getToken()}`;
                    return Promise.resolve(config);
                });
            } else {
                return Promise.resolve(config);
            }
        };
        return interceptor;
    }

    // @ts-ignore
    private updateKeycloakToken = (successCallback) => {
        return this.keycloak.updateToken(5)
            .then(successCallback)
            .catch(this.doLogin)
    };
}
