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

import {Services} from "../../services";
import {PureComponent} from "../components";

/**
 * Component properties shared by all federated pages.
 */
export interface FederatedPageProps {
    tenantId: string;
    navPrefixPath: string;
    history: any;
}

export class FederatedUtils {

    static updateConfiguration(props: FederatedPageProps): void {
        // @ts-ignore
        const tenantUrl: string = Services.getConfigService().featureMultiTenantUrl().replace(":tenantId", props.tenantId);
        Services.getLoggerService().info("[FederatedUtils] Setting registry API to: %s", tenantUrl);
        Services.getConfigService().setArtifactsUrl(tenantUrl);
        // @ts-ignore
        Services.getLoggerService().info("[FederatedUtils] Using nav prefix: ", props.navPrefixPath);
        Services.getConfigService().setUiNavPrefixPath(props.navPrefixPath);
        PureComponent.setHistory(props.history);
    }

}
