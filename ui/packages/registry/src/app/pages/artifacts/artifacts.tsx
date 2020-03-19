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
import {Flex, FlexItem, PageSection, PageSectionVariants, Spinner, TextContent} from '@patternfly/react-core';
import {ArtifactsPageHeader} from "./components/pageheader";
import {ArtifactsToolbar} from "./components/toolbar";
import "./artifacts.css";
import {ArtifactsEmptyState} from "./components/empty";
import {Artifact} from "@apicurio/registry-models";
import {Services} from "@apicurio/registry-services";
import {ArtifactList} from "./components/artifactList";


/**
 * Properties
 */
export interface ArtifactsProps {

}

/**
 * State
 */
export interface ArtifactsState {
    artifacts: Artifact[];
    isLoading: boolean;
}

/**
 * The artifacts page.
 */
export class Artifacts extends React.PureComponent<ArtifactsProps, ArtifactsState> {

    constructor(props: Readonly<ArtifactsProps>) {
        super(props);
        this.state = {
            artifacts: [],
            isLoading: true
        };
        Services.getArtifactsService().getArtifacts().then( artifacts => {
            this.onArtifactsLoaded(artifacts);
        }).then(error => {
            // TODO handle errors!
        });
    }

    public render(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_artifacts-header" variant={PageSectionVariants.light}>
                    <ArtifactsPageHeader/>
                </PageSection>
                <PageSection variant={PageSectionVariants.light} noPadding={true}>
                    <ArtifactsToolbar artifactsCount={this.state.artifacts.length} />
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    {
                        this.state.isLoading ?
                            <Flex>
                                <FlexItem><Spinner size="lg" /></FlexItem>
                                <FlexItem><span>Loading, please wait...</span></FlexItem>
                            </Flex>
                        : this.state.artifacts.length === 0 ?
                            <ArtifactsEmptyState isFiltered={false}/>
                        :
                            <ArtifactList artifacts={this.state.artifacts} />
                    }
                </PageSection>
            </React.Fragment>
        );
    }
    
    private onArtifactsLoaded(artifacts: Artifact[]): void {
        this.setState({
            ...this.state,
            artifacts,
            isLoading: false
        });
    }

}
