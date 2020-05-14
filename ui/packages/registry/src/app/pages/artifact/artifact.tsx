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

import React from "react";
import {PageComponent, PageProps, PageState} from "../basePage";
import {Redirect} from "react-router";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactRedirectPageProps extends PageProps {
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactRedirectPageState extends PageState {
}

/**
 * The artifact details redirect page.
 */
export class ArtifactRedirectPage extends PageComponent<ArtifactRedirectPageProps, ArtifactRedirectPageState> {

    constructor(props: Readonly<ArtifactRedirectPageProps>) {
        super(props);
    }

    public renderPage(): React.ReactElement {
        const artifactId: any = this.getPathParam("artifactId");
        const redirect: string = `/artifacts/${ encodeURIComponent(artifactId) }/versions/latest`;
        return (
            <Redirect to={redirect}  />
        );
    }

    protected initializeState(): ArtifactRedirectPageState {
        return {};
    }
}
