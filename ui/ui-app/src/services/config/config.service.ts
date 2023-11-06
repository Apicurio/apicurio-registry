import {
    Alerts,
    ConfigType,
    FeaturesConfig,
    GetTokenAuthConfig,
    OidcJsAuthConfig,
    OidcJsAuthOptions
} from "./config.type";
import { Service } from "../baseService";

const DEFAULT_CONFIG: ConfigType = {
    artifacts: {
        url: "http://localhost:8080/apis/registry/v2"
    },
    auth: {
        options: {
            url: "http://localhost:8090/realms/apicurio",
            clientId: "registry-ui",
            redirectUri: "http://localhost:8888",
            scopes: "openid profile email offline_token"
        },
        type: "oidc",
        rbacEnabled: true,
        obacEnabled: false
    },
    features: {
        readOnly: false,
        breadcrumbs: true,
        roleManagement: true,
        settings: true
    },
    ui: {
        contextPath: "/",
        navPrefixPath: "/",
        oaiDocsUrl: "http://localhost:8889"
    }
};


export function getRegistryConfig(): ConfigType {
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    if (ApicurioRegistryConfig) { return ApicurioRegistryConfig as ConfigType; }

    const gw: any = window as any;
    if (gw["ApicurioRegistryConfig"]) {
        return gw["ApicurioRegistryConfig"] as ConfigType;
    }

    return DEFAULT_CONFIG;
}


/**
 * A simple configuration service.  Reads information from a global "ApicurioRegistryConfig" variable
 * that is typically included via JSONP.
 */
export class ConfigService implements Service {
    private config: ConfigType;

    constructor() {
        this.config = getRegistryConfig();
    }

    public init(): void {
        // Nothing to init (done in c'tor)
    }

    public updateConfig(config: ConfigType): void {
        this.config = config;
    }

    public artifactsUrl(): string|null {
        return this.config.artifacts.url || null;
    }

    public uiContextPath(): string|undefined {
        return this.config.ui.contextPath || "/";
    }

    public uiOaiDocsUrl(): string {
        return this.config.ui.oaiDocsUrl || "http://localhost:8889";
    }

    public uiNavPrefixPath(): string|undefined {
        if (!this.config.ui || !this.config.ui.navPrefixPath) {
            return "";
        }
        if (this.config.ui.navPrefixPath.endsWith("/")) {
            this.config.ui.navPrefixPath = this.config.ui.navPrefixPath.substr(0, this.config.ui.navPrefixPath.length - 1);
        }
        return this.config.ui.navPrefixPath;
    }

    public features(): FeaturesConfig {
        const defaults: FeaturesConfig = {
            readOnly: false,
            breadcrumbs: true
        };
        if (!this.config.features) {
            return defaults;
        }
        return {
            ...defaults,
            ...this.config.features
        };
    }

    public featureReadOnly(): boolean {
        return this.features().readOnly || false;
    }

    public featureBreadcrumbs(): boolean {
        return this.features().breadcrumbs || false;
    }

    public featureRoleManagement(): boolean {
        return this.features().roleManagement || false;
    }

    public featureSettings(): boolean {
        return this.features().settings || false;
    }

    public featureAlertsService(): Alerts | undefined {
        return this.features().alerts;
    }

    public authType(): string {
        if (!this.config.auth || !this.config.auth.type) {
            return "";
        }
        return this.config.auth.type;
    }

    public authRbacEnabled(): boolean {
        if (!this.config.auth || !this.config.auth.rbacEnabled) {
            return false;
        }
        return this.config.auth.rbacEnabled;
    }

    public authObacEnabled(): boolean {
        if (!this.config.auth || !this.config.auth.obacEnabled) {
            return false;
        }
        return this.config.auth.obacEnabled;
    }

    public authOptions(): OidcJsAuthOptions {
        if (this.config.auth) {
            const auth: OidcJsAuthConfig = this.config.auth as OidcJsAuthConfig;
            return auth.options;
        }
        return {} as any;
    }

    public authGetToken(): () => Promise<string> {
        if (this.config.auth) {
            const auth: GetTokenAuthConfig = this.config.auth as GetTokenAuthConfig;
            return auth.getToken;
        }
        return () => {
            console.error("[ConfigService] Missing: 'getToken' from auth config.");
            return Promise.resolve("");
        };
    }

}
