/**
 * @license
 * Copyright 2020 JBoss Inc
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

import { Alerts, ConfigType, FeaturesConfig, GetTokenAuthConfig, OidcJsAuthConfig, Principal } from "./config.type";
import { Service } from "../baseService";

const DEFAULT_CONFIG: ConfigType = {
    artifacts: {
        url: "http://localhost:8080/apis/registry"
    },
    auth: {
        options: {
            clientId: "registry-ui",
            onLoad: "login-required",
            realm: "registry",
            url: "http://localhost:8090/auth"
        },
        type: "keycloakjs",
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
        navPrefixPath: "/"
    }
};

/**
 * A simple configuration service.  Reads information from a global "ApicurioRegistryConfig" variable
 * that is typically included via JSONP.
 */
export class ConfigService implements Service {
    private config: ConfigType;

    constructor() {
        const w: any = window;
        if (w.ApicurioRegistryConfig) {
            this.config = w.ApicurioRegistryConfig;
            console.info("[ConfigService] Found app config.");
        } else {
            console.warn("[ConfigService] App config not found! (using default)");
            this.config = DEFAULT_CONFIG;
        }
    }

    public init(): void {
        // Nothing to init (done in c'tor)
    }

    public updateConfig(config: ConfigType): void {
        this.config = config;
    }

    public artifactsUrl(): string|null {
        if (!this.config.artifacts) {
            return null;
        }
        return this.config.artifacts.url;
    }

    public uiContextPath(): string|undefined {
        if (!this.config.ui || !this.config.ui.contextPath) {
            return "/";
        }
        return this.config.ui.contextPath;
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

    public authOptions(): any {
        if (this.config.auth) {
            const auth: OidcJsAuthConfig = this.config.auth as OidcJsAuthConfig;
            return auth.options;
        }
        return {};
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

    public principals(): Principal[] | (() => Principal[]) | undefined {
        return this.config.principals;
    }

    public featureMultiTenant(): boolean {
        return this.features().multiTenant || false;
    }
}
