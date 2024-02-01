import { createEndpoint, httpGet } from "@utils/rest.utils.ts";
import { cloneObject } from "@utils/object.utils.ts";

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


function difference(base: any, overrides: any | undefined): any {
    const rval: any = cloneObject(overrides);

    // Remove any properties that exist in base.
    Object.getOwnPropertyNames(base).forEach(propertyName => {
        if (typeof rval[propertyName] !== "object") {
            delete rval[propertyName];
        }
    });

    // Now diff any remaining props that are objects
    Object.getOwnPropertyNames(rval).forEach(propertyName => {
        const value: any = rval[propertyName];
        const baseValue: any = base[propertyName];
        if (typeof value === "object") {
            rval[propertyName] = difference(baseValue, value);
        }
    });

    // Now remove any properties with empty object values.
    Object.getOwnPropertyNames(rval).forEach(propertyName => {
        if (typeof rval[propertyName] === "object" && Object.keys(rval[propertyName]).length === 0) {
            delete rval[propertyName];
        }
    });

    return rval;
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
    return overrideObject(base, overrides);
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

        const localConfig: ApicurioRegistryConfig = registryConfig;

        console.info("[Config] Fetching UI configuration from: ", endpoint);
        return httpGet<ApicurioRegistryConfig>(endpoint).then(remoteConfig => {
            console.info("[Config] UI configuration fetched successfully: ", remoteConfig);
            // Always use the local config's "artifacts" property (contains the REST API endpoint)
            remoteConfig.artifacts = localConfig.artifacts;
            // Override the remote config with anything in the local config.  Then set the result
            // as the new official app config.
            registryConfig = overrideConfig(remoteConfig, localConfig);
            // Check for extra/unknown local config and warn about it.
            const diff: any = difference(remoteConfig, localConfig);
            if (Object.keys(diff).length > 0) {
                console.warn("[Config] Local config contains unexpected properties: ", diff);
            }
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
