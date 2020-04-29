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
import {
    DocumentationTabContent,
    ContentTabContent,
    InfoTabContent,
    VersionsTabContent
} from "./components/tabs";
import {Services} from "@apicurio/registry-services";
import {Rule} from "@apicurio/registry-models";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactPageProps extends PageProps {
}

/**
 * State
 */
export interface ArtifactPageState extends PageState {
    activeTabKey: number;
    artifact: ArtifactMetaData | null;
    artifactContent: string;
    artifactIsText: boolean;
    isUploadModalOpen: boolean;
    rules: Rule[] | null;
}

/**
 * The artifacts page.
 */
export class ArtifactPage extends PageComponent<ArtifactPageProps, ArtifactPageState> {

    constructor(props: Readonly<ArtifactPageProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        const artifact: ArtifactMetaData = this.state.artifact ? this.state.artifact : new ArtifactMetaData();
        const tabs: React.ReactNode[] = [
            <Tab eventKey={0} title="Info" key="info" tabContentId="tab-info">
                <InfoTabContent artifact={artifact}
                                rules={this.rules()}
                                doEnableRule={this.doEnableRule}
                                doDisableRule={this.doDisableRule}
                                doConfigureRule={this.doConfigureRule}
                />
            </Tab>,
            <Tab eventKey={1} title="Documentation" key="documentation">
                <DocumentationTabContent artifactContent={this.state.artifactContent} artifactType={artifact.type} />
            </Tab>,
            <Tab eventKey={2} title="Content" key="content">
                <ContentTabContent artifactContent={this.state.artifactContent} />
            </Tab>,
            <Tab eventKey={3} title="Versions" key="versions">
                <VersionsTabContent />
            </Tab>
        ];
        if (!this.showDocumentationTab()) {
            tabs.splice(1, 1);
        }

        return (
            <React.Fragment>
                <PageSection className="ps_artifacts-header" variant={PageSectionVariants.light}>
                    <ArtifactPageHeader onUploadVersion={this.onUploadVersion} />
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
                <PageSection variant={PageSectionVariants.light} isFilled={true} noPadding={true} className="artifact-details-main">
                    <Tabs className="artifact-page-tabs"
                          unmountOnExit={true}
                          isFilled={true}
                          activeKey={this.state.activeTabKey}
                          children={tabs}
                          onSelect={this.handleTabClick}
                    />
                </PageSection>
                }
            </React.Fragment>
        );
    }

    protected initializeState(): ArtifactPageState {
        return {
            activeTabKey: 0,
            artifact: null,
            artifactContent: "",
            artifactIsText: true,
            isLoading: true,
            isUploadModalOpen: false,
            rules: null
        };
    }

    protected loadPageData(): void {
        // @ts-ignore
        const artifactId: any = this.props.match.params.artifactId;
        Services.getLoggerService().info("Loading data for artifact: ", artifactId);

        Promise.all([
            Services.getArtifactsService().getArtifactMetaData(artifactId).then(md => this.setSingleState("artifact", md)),
            Services.getArtifactsService().getArtifactContent(artifactId).then(content => this.setSingleState("artifactContent", content)),
            Services.getArtifactsService().getArtifactRules(artifactId).then(rules => this.setSingleState("rules", rules))
        ]).then( () => {
            this.setSingleState("isLoading", false);
        }).catch( error => {
            // Handle errors!!!
        });
    }

    private handleTabClick = (event: any, tabIndex: any): void => {
        this.setSingleState("activeTabKey", tabIndex);
    };

    private onUploadVersion = (): void => {
        this.setSingleState("isUploadModalOpen", true);
    };

    private getArtifactContent(): string {
        if (this.state.artifactContent != null) {
            return JSON.stringify(this.state.artifactContent, null, 4);
        } else {
            return "";
        }
    }

    private showDocumentationTab(): boolean {
        if (this.state.artifact) {
            return this.state.artifact.type === "OPENAPI" || this.state.artifact.type === "ASYNCAPI";
        } else {
            return false;
        }
    }

    private rules(): Rule[] {
        if (this.state.rules) {
            return this.state.rules;
        } else {
            return [];
        }
    }

    private doEnableRule = (ruleType: string): void => {
        Services.getLoggerService().debug("[ArtifactPage] Enabling rule:", ruleType);
        let config: string = "FULL";
        if (ruleType === "COMPATIBILITY") {
            config = "BACKWARD";
        }
        Services.getGlobalsService().updateRule(ruleType, config).catch(error => {
            // TODO handle this error!
        });
        this.setSingleState("rules", [...this.rules(), Rule.create(ruleType, config)])
    };

    private doDisableRule = (ruleType: string): void => {
        Services.getLoggerService().debug("[ArtifactPage] Disabling rule:", ruleType);
        Services.getGlobalsService().updateRule(ruleType, null).catch(error => {
            // TODO handle this error!
        });
        this.setSingleState("rules", this.rules().filter(r=>r.type !== ruleType));
    };

    private doConfigureRule = (ruleType: string, config: string): void => {
        Services.getLoggerService().debug("[ArtifactPage] Configuring rule:", ruleType, config);
        Services.getGlobalsService().updateRule(ruleType, config).catch(error => {
            // TODO handle this error!
        });
        this.setSingleState("rules", this.rules().map(r => {
            if (r.type === ruleType) {
                return Rule.create(r.type, config);
            } else {
                return r;
            }
        }));
    };

}
