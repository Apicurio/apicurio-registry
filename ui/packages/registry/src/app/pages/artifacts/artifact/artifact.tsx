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
import {PageSection, PageSectionVariants} from '@patternfly/react-core';
import "./artifact.css";
import {PageComponent, PageProps, PageState} from "../../basePage";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactPageProps extends PageProps {
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactPageState extends PageState {
}

/**
 * The artifacts page.
 */
export class ArtifactPage extends PageComponent<ArtifactPageProps, ArtifactPageState> {

    constructor(props: Readonly<ArtifactPageProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_artifacts-header" variant={PageSectionVariants.light}>
                    <h1>Artifact Details</h1>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    <h2>Content goes here (TBD)</h2>
                </PageSection>
            </React.Fragment>
        );
    }

    protected initializeState(): ArtifactPageState {
        return {
            isLoading: false
        };
    }

}
