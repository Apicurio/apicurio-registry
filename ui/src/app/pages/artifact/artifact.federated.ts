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

import {ArtifactRedirectPage, ArtifactRedirectPageProps} from "./artifact";
import {FederatedPageProps, FederatedUtils} from "../../federated";

export interface FederatedArtifactRedirectPageProps extends ArtifactRedirectPageProps, FederatedPageProps {
    groupId: string;
    artifactId: string;
}

export default class FederatedArtifactRedirectPage extends ArtifactRedirectPage {

    constructor(props: Readonly<FederatedArtifactRedirectPageProps>) {
        super(props);
    }

    protected postConstruct(): void {
        FederatedUtils.updateConfiguration(this.props as FederatedPageProps);
        super.postConstruct();
    }

    protected groupIdParam(): string {
        return (<FederatedArtifactRedirectPageProps>this.props).groupId;
    }

    protected artifactIdParam(): string {
        return (<FederatedArtifactRedirectPageProps>this.props).artifactId;
    }

}
