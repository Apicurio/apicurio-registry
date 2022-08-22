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

import { ArtifactVersionPage, ArtifactVersionPageProps } from "./artifactVersion";
import { FederatedComponentProps, FederatedUtils } from "../../federated";

export interface FederatedArtifactsPageProps extends ArtifactVersionPageProps, FederatedComponentProps {
    groupId: string;
    artifactId: string;
    version: string;
}

export default class FederatedArtifactVersionPage extends ArtifactVersionPage {

    constructor(props: Readonly<FederatedArtifactsPageProps>) {
        super(props);
    }

    protected postConstruct(): void {
        FederatedUtils.updateConfiguration(this.props as FederatedComponentProps);
        super.postConstruct();
    }

    protected groupIdParam(): string {
        return (this.props as FederatedArtifactsPageProps).groupId;
    }

    protected artifactIdParam(): string {
        return (this.props as FederatedArtifactsPageProps).artifactId;
    }

    protected versionParam(): string {
        return (this.props as FederatedArtifactsPageProps).version;
    }

}
