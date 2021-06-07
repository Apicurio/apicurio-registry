/**
 * @license
 * Copyright 2021 Red Hat
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

import {ArtifactsPage, ArtifactsPageProps} from "./artifacts";
import {Services} from "../../../services";

export interface FederatedArtifactsPageProps extends ArtifactsPageProps {
    tenantId: string;
}

export default class FederatedArtifactsPage extends ArtifactsPage {

    constructor(props: Readonly<FederatedArtifactsPageProps>) {
        super(props);
    }

    protected postConstruct(): void {
        // @ts-ignore
        const tenantUrl: string = Services.getConfigService().featureMultiTenantUrl().replace("$tenantId", this.props.tenantId);
        Services.getLoggerService().info("[FederatedArtifactsPage] Setting registry API to: %s", tenantUrl);
        Services.getConfigService().setArtifactsUrl(tenantUrl);
        Services.getLoggerService().info("[FederatedArtifactsPage] Registry API is now: %s", Services.getConfigService().artifactsUrl());

        super.postConstruct();
    }

}
