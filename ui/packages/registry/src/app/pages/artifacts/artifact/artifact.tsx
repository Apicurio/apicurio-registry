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
import {Flex, FlexItem, PageSection, PageSectionVariants, Spinner, Tab, Tabs} from '@patternfly/react-core';
import "./artifact.css";
import {PageComponent, PageProps, PageState} from "../../basePage";
import {ArtifactPageHeader} from "./components/pageheader";
import {ArtifactMetaData} from "@apicurio/registry-models";
import {ApiDocumentationTabContent, ContentTabContent, InfoTabContent, VersionsTabContent} from "./components/tabs";


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
    activeTabKey: number;
    artifact: ArtifactMetaData | null;
    artifactContent: object | undefined;
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
                    <ArtifactPageHeader onUploadVersion={console.info} />
                </PageSection>
                {
                    this.state.isLoading ?
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    <Flex>
                        <FlexItem><Spinner size="lg"/></FlexItem>
                        <FlexItem><span>Loading, please wait...</span></FlexItem>
                    </Flex>
                </PageSection>
                    :
                <PageSection variant={PageSectionVariants.default} isFilled={true} noPadding={true}>
                    <Tabs className="artifact-page-tabs" unmountOnExit={true} isFilled={true} activeKey={this.state.activeTabKey} onSelect={this.handleTabClick}>
                        <Tab eventKey={0} title="Artifact Info">
                            <InfoTabContent />
                        </Tab>
                        <Tab eventKey={1} title="Content">
                            <ContentTabContent />
                        </Tab>
                        { this.isOpenApi() ?
                            <Tab eventKey={2} title="API Documentation">
                                <ApiDocumentationTabContent />
                            </Tab>
                            :
                            null
                        }
                        <Tab eventKey={3} title="Versions">
                            <VersionsTabContent />
                        </Tab>
                    </Tabs>
                </PageSection>
                }
            </React.Fragment>
        );
    }

    protected initializeState(): ArtifactPageState {
        return {
            activeTabKey: 0,
            artifact: null,
            artifactContent: undefined,
            isLoading: true
        };
    }

    protected loadPageData(): void {
        const oaiContent: any = {};
        const artifact: ArtifactMetaData = new ArtifactMetaData();
        artifact.id = "1";
        artifact.name = "Example API";
        artifact.description = "This is just an example API";
        artifact.type = "OPENAPI";
        artifact.version = 1;
        artifact.createdBy = "user";
        artifact.createdOn = new Date();
        artifact.modifiedBy = "user";
        artifact.modifiedOn = new Date();
        artifact.globalId = 14298431927;
        artifact.state = "ENABLED";
        setTimeout( () => {
            this.setMultiState({
                artifact,
                artifactContent: oaiContent,
                isLoading: false
            });
        }, 500);
    }

    private handleTabClick = (event: any, tabIndex: any): void => {
        this.setSingleState("activeTabKey", tabIndex);
    };

    private getArtifactContent(): string {
        if (this.state.artifactContent != null) {
            return JSON.stringify(this.state.artifactContent, null, 4);
        } else {
            return "";
        }
    }

    private isOpenApi(): boolean {
        if (this.state.artifact) {
            return this.state.artifact.type === "OPENAPI";
        } else {
            return false;
        }
    }
}
