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

import { ConfigType } from './config.type';

const DEFAULT_CONFIG: ConfigType = {
  artifacts: {
    url: "http://localhost:8080/",
    type: "rest"
  },
  features: {
  },
  mode: "dev",
  ui: {
    uiUrl: "http://localhost:8888/"
  }
};

/**
 * A simple configuration service.  Reads information from a global "ApicurioRegistryConfig" variable
 * that is typically included via JSONP.
 */
export class ConfigService {
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

}
