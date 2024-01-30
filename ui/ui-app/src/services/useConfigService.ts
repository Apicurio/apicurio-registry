import { createEndpoint, httpGet } from "@utils/rest.utils.ts";

export enum AlertVariant {
    success = "success",
    danger = "danger",
    warning = "warning",
    info = "info",
    default = "default"
}

export type AlertProps = {
    /**
     * Unique key
     */
    id?: string;
    /**
     * Flag to automatically call `onDismiss` after `dismissDelay` runs out.
     */
    autoDismiss?: boolean;
    /**
     * Flag to show/hide notification close button.
     */
    dismissable?: boolean;
    /**
     * Alert variant
     */
    variant: AlertVariant;
    /**
     * Alert title
     */
    title: string;
    /**
     * Alert description
     */
    description?: string;
    /**
     * Time period after which `onDismiss` is called.
     */
    dismissDelay?: number;
    /**
     * Unique request ID.
     */
    requestId?: string;
    /**
     * data-testid attribute
     */
    dataTestId?: string;
};

export declare type Alerts = {
    addAlert: ({ id, title, variant, description, dataTestId, autoDismiss, dismissable, dismissDelay, requestId }: AlertProps) => void;
};


export interface FeaturesConfig {
    showMasthead?: boolean;
    readOnly?: boolean;
    breadcrumbs?: boolean;
    roleManagement?: boolean;
    settings?: boolean;
    alerts?: Alerts;
}

export interface ArtifactsConfig {
    url: string;
}

export interface UiConfig {
    contextPath?: string;
    navPrefixPath?: string;
    oaiDocsUrl?: string;
}

export interface AuthConfig {
    type: string;
    rbacEnabled: boolean;
    obacEnabled: boolean;
}

export interface OidcJsAuthOptions {
    url: string;
    redirectUri: string;
    clientId: string;
    scope: string;
}

// Used when `type=keycloakjs`
export interface OidcJsAuthConfig extends AuthConfig {
    options: OidcJsAuthOptions;
}

// Used when `type=none`
export type NoneAuthConfig = AuthConfig;


// Used when `type=gettoken`
export interface GetTokenAuthConfig extends AuthConfig {
    getToken: () => Promise<string>;
}

export interface Principal {
    principalType: "USER_ACCOUNT" | "SERVICE_ACCOUNT";
    id: string;
    displayName?: string;
    emailAddress?: string;
}

export interface ConfigType {
    artifacts: ArtifactsConfig;
    auth?: OidcJsAuthConfig | NoneAuthConfig | GetTokenAuthConfig;
    principals?: Principal[] | (() => Principal[]);
    features?: FeaturesConfig;
    ui?: UiConfig;
}

export interface ApicurioRegistryConfig extends ConfigType {
    // Rename
}


export function getRegistryConfig(): ApicurioRegistryConfig {
    let config: ApicurioRegistryConfig | undefined;

    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    if (ApicurioRegistryConfig) { config = ApicurioRegistryConfig as ApicurioRegistryConfig; }

    const gw: any = window as any;
    if (gw["ApicurioRegistryConfig"]) {
        config = gw["ApicurioRegistryConfig"] as ApicurioRegistryConfig;
    }

    if (!config) {
        throw new Error("ApicurioRegistryConfig not found.");
    }

    return config;
}

function overrideObject(base: any, overrides: any | undefined): any {
    if (overrides === undefined) {
        return {
            ...base
        };
    }
    const rval: any = {};
    Object.getOwnPropertyNames(base).forEach(propertyName => {
        const baseValue: any = base[propertyName];
        const overrideValue: any = overrides[propertyName];
        if (overrideValue) {
            if (typeof baseValue === "object" && typeof overrideValue === "object") {
                rval[propertyName] = overrideObject(baseValue, overrideValue);
            } else {
                rval[propertyName] = overrideValue;
            }
        } else {
            rval[propertyName] = baseValue;
        }
    });
    return rval;
}

function overrideConfig(base: ApicurioRegistryConfig, overrides: ApicurioRegistryConfig): ApicurioRegistryConfig {
    const rval: ApicurioRegistryConfig = overrideObject(base, overrides);
    // Make sure to use the local (overrides) artifacts property, since that has the
    // REST API endpoint (which is pretty important).
    rval.artifacts = overrides.artifacts;
    return rval;
}

let registryConfig: ApicurioRegistryConfig = getRegistryConfig();


export interface ConfigService {

    fetchAndMergeConfigs(): Promise<void>;
    artifactsUrl(): string;
    uiContextPath(): string|undefined;
    uiOaiDocsUrl(): string;
    uiNavPrefixPath(): string|undefined;
    features(): FeaturesConfig;
    featureReadOnly(): boolean;
    featureBreadcrumbs(): boolean;
    featureRoleManagement(): boolean;
    featureSettings(): boolean;
    authType(): string;
    authRbacEnabled(): boolean;
    authObacEnabled(): boolean;
    authOptions(): OidcJsAuthOptions;
    authGetToken(): () => Promise<string>;

}


export class ConfigServiceImpl implements ConfigService {

    public fetchAndMergeConfigs(): Promise<void> {
        const endpoint: string = createEndpoint(this.artifactsUrl(), "/system/uiConfig");
        console.info("[Config] Fetching UI configuration from: ", endpoint);
        return httpGet<ApicurioRegistryConfig>(endpoint).then(config => {
            console.info("[Config] UI configuration fetched successfully: ", config);
            registryConfig = overrideConfig(config, registryConfig);
        }).catch(error => {
            console.error("[Config] Error fetching UI configuration: ", error);
            console.error("------------------------------------------");
            console.error("[Config] Note: using local UI config only!");
            console.error("------------------------------------------");
            return Promise.resolve();
        });
    }

    public artifactsUrl(): string {
        return registryConfig.artifacts.url || "http://localhost:8080/apis/registry/v3/";
    }

    public uiContextPath(): string|undefined {
        return registryConfig.ui?.contextPath || "/";
    }

    public uiOaiDocsUrl(): string {
        return registryConfig.ui?.oaiDocsUrl || "/docs";
    }

    public uiNavPrefixPath(): string|undefined {
        if (!registryConfig.ui || !registryConfig.ui.navPrefixPath) {
            return "";
        }
        if (registryConfig.ui.navPrefixPath.endsWith("/")) {
            registryConfig.ui.navPrefixPath = registryConfig.ui.navPrefixPath.substr(0, registryConfig.ui.navPrefixPath.length - 1);
        }
        return registryConfig.ui.navPrefixPath;
    }

    public features(): FeaturesConfig {
        const defaults: FeaturesConfig = {
            readOnly: false,
            breadcrumbs: true
        };
        if (!registryConfig.features) {
            return defaults;
        }
        return {
            ...defaults,
            ...registryConfig.features
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
        return this.features().settings || true;
    }

    public authType(): string {
        return registryConfig.auth?.type || "none";
    }

    public authRbacEnabled(): boolean {
        return registryConfig.auth?.rbacEnabled || false;
    }

    public authObacEnabled(): boolean {
        return registryConfig.auth?.obacEnabled || false;
    }

    public authOptions(): OidcJsAuthOptions {
        if (registryConfig.auth) {
            const auth: OidcJsAuthConfig = registryConfig.auth as OidcJsAuthConfig;
            return auth.options || {};
        }
        return {} as any;
    }

    public authGetToken(): () => Promise<string> {
        if (registryConfig.auth) {
            const auth: GetTokenAuthConfig = registryConfig.auth as GetTokenAuthConfig;
            return auth.getToken;
        }
        return () => {
            console.error("[ConfigService] Missing: 'getToken' from auth config.");
            return Promise.resolve("");
        };
    }

}

const configService: ConfigService = new ConfigServiceImpl();

/**
 * React hook to get the app config.
 */
export const useConfigService: () => ConfigService = (): ConfigService => {
    return configService;
};
