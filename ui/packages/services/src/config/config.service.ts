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

import {ConfigType, FeaturesConfig} from './config.type';
import {Service} from "../baseService";

const DEFAULT_CONFIG: ConfigType = {
    artifacts: {
        type: "rest",
        url: "http://localhost:8080/api/"
    },
    features: {
        readOnly: false
    },
    auth: {
        url: 'http://localhost:8090/auth',
        realm: 'registry',
        clientId:'registry-ui',
        onLoad: 'login-required'
    },
    mode: "dev",
    ui: {
        contextPath: null,
        url: "http://localhost:8888/"
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
            console.error("[ConfigService] App config not found! (using default)");
            this.config = DEFAULT_CONFIG;
        }
    }

    public init(): void {
        // Nothing to init (done in c'tor)
    }

    public artifactsType(): string {
        if (!this.config.artifacts) {
            return null;
        }
        return this.config.artifacts.type;
    }

    public artifactsUrl(): string {
        if (!this.config.artifacts) {
            return null;
        }
        return this.config.artifacts.url;
    }

    public uiUrl(): string {
        if (!this.config.ui || !this.config.ui.url) {
            return "";
        }
        return this.config.ui.url;
    }

    public uiContextPath(): string|undefined {
        if (!this.config.ui || !this.config.ui.contextPath) {
            return undefined;
        }
        return this.config.ui.contextPath;
    }

    public features(): FeaturesConfig {
        if (!this.config.features) {
            return {};
        }
        return this.config.features;
    }

    public featureReadOnly(): boolean {
        if (!this.config.features || !this.config.features.readOnly) {
            return false;
        }
        return this.config.features.readOnly;
    }

    public authUrl(): string {
        if (!this.config.auth || !this.config.auth.url) {
            return "";
        }
        return this.config.auth.url;
    }

    public authRealm(): string {
        if (!this.config.auth || !this.config.auth.realm) {
            return "";
        }
        return this.config.auth.realm;
    }

    public authClientId(): string {
        if (!this.config.auth || !this.config.auth.clientId) {
            return "";
        }
        return this.config.auth.clientId;
    }

    public authOnLoad(): string {
        if (!this.config.auth || !this.config.auth.onLoad) {
            return "";
        }
        return this.config.auth.onLoad;
    }
}
