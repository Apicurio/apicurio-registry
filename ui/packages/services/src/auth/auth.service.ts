import Keycloak from "keycloak-js";
import {ConfigService} from "../config";
import {Service} from "../baseService";


/**
 * Initializes Keycloak instance and calls the provided callback function if successfully authenticated.
 *
 * @param onAuthenticatedCallback
 */

export class AuthService implements Service {

    private config: ConfigService = null;
    private _kc: Keycloak.KeycloakInstance;

    public init = () => {
    }

    // @ts-ignore
    public initKeycloak = (onAuthenticatedCallback) => {

        let initOptions = {
            url: this.config.authUrl(),
            realm: this.config.authRealm(),
            clientId: this.config.authClientId(),
            onLoad: this.config.authOnLoad()
        };

        this._kc = Keycloak(initOptions)

        this._kc.init({onLoad: 'login-required'})
            .then((authenticated) => {
                if (authenticated) {
                    onAuthenticatedCallback();
                } else {
                    console.warn("Not authenticated!");
                    this.doLogin();
                }
            })
    };

    public doLogin = () => this._kc.login;

    public doLogout = () =>  this._kc.logout;

    public getToken = () => this._kc.token;

    // @ts-ignore
    public updateToken = (successCallback) => {
        return this._kc.updateToken(5)
            .then(successCallback)
            .catch(this.doLogin)
    };
}