/**
 * @license
 * Copyright 2022 Red Hat Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
     * Unique sentry error ID.
     */
    sentryId?: string;
    /**
     * data-testid attribute
     */
    dataTestId?: string;
};

export declare type Alerts = {
    addAlert: ({ id, title, variant, description, dataTestId, autoDismiss, dismissable, dismissDelay, requestId, sentryId }: AlertProps) => void;
};


export interface FeaturesConfig {
    readOnly?: boolean;
    breadcrumbs?: boolean;
    multiTenant?: boolean;
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
}

export interface AuthConfig {
    type: string;
    rbacEnabled: boolean;
    obacEnabled: boolean;
}

// Used when `type=keycloakjs`
export interface OidcJsAuthConfig extends AuthConfig {
    options?: any;
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
    auth: OidcJsAuthConfig | NoneAuthConfig | GetTokenAuthConfig;
    principals?: Principal[] | (() => Principal[]);
    features?: FeaturesConfig;
    ui: UiConfig;
}
