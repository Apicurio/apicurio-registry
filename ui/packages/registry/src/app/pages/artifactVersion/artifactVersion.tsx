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
import "./artifactVersion.css";
import {
    Button,
    Flex,
    FlexItem,
    Modal,
    PageSection,
    PageSectionVariants,
    Spinner,
    Tab,
    Tabs
} from '@patternfly/react-core';
import {PageComponent, PageProps, PageState} from "../basePage";
import {ArtifactMetaData, Rule, VersionMetaData} from "@apicurio/registry-models";
import {ContentTabContent, DocumentationTabContent, InfoTabContent} from "./components/tabs";
import {CreateArtifactData, CreateVersionData, Services} from "@apicurio/registry-services";
import {ArtifactVersionPageHeader} from "./components/pageheader";
import {UploadVersionForm} from "./components/uploadForm";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactVersionPageProps extends PageProps {
}

/**
 * State
 */
export interface ArtifactVersionPageState extends PageState {
    activeTabKey: number;
    artifact: ArtifactMetaData | null;
    artifactContent: string;
    artifactIsText: boolean;
    isUploadFormValid: boolean;
    isUploadModalOpen: boolean;
    rules: Rule[] | null;
    uploadFormData: string | null;
    versions: VersionMetaData[] | null;
}

/**
 * The artifacts page.
 */
export class ArtifactVersionPage extends PageComponent<ArtifactVersionPageProps, ArtifactVersionPageState> {

    constructor(props: Readonly<ArtifactVersionPageProps>) {
        super(props);
    }

    public renderPage(): React.ReactElement {
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
        ];
        if (!this.showDocumentationTab()) {
            tabs.splice(1, 1);
        }

        return (
            <React.Fragment>
                <PageSection className="ps_artifacts-header" variant={PageSectionVariants.light}>
                    <ArtifactVersionPageHeader onUploadVersion={this.onUploadVersion} versions={this.versions()} version={this.version()} artifactId={this.artifactId()} />
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
                <Modal
                    title="Upload Artifact Version"
                    isLarge={true}
                    isOpen={this.state.isUploadModalOpen}
                    onClose={this.onUploadModalClose}
                    className="upload-artifact-modal pf-m-redhat-font"
                    actions={[
                        <Button key="upload" variant="primary" onClick={this.doUploadArtifact} isDisabled={!this.state.isUploadFormValid}>Upload</Button>,
                        <Button key="cancel" variant="link" onClick={this.onUploadModalClose}>Cancel</Button>
                    ]}
                >
                    <UploadVersionForm onChange={this.onUploadFormChange} onValid={this.onUploadFormValid} />
                </Modal>
            </React.Fragment>
        );
    }

    protected initializeState(): ArtifactVersionPageState {
        return {
            activeTabKey: 0,
            artifact: null,
            artifactContent: "",
            artifactIsText: true,
            error: null,
            errorInfo: null,
            errorType: null,
            isError: false,
            isLoading: true,
            isUploadFormValid: false,
            isUploadModalOpen: false,
            rules: null,
            uploadFormData: null,
            versions: null
        };
    }

    protected loadPageData(): void {
        const artifactId: string = this.getPathParam("artifactId");
        Services.getLoggerService().info("Loading data for artifact: ", artifactId);

        Promise.all([
            Services.getArtifactsService().getArtifactMetaData(artifactId, this.version()).then(md => this.setSingleState("artifact", md)),
            Services.getArtifactsService().getArtifactContent(artifactId, this.version()).then(content => this.setSingleState("artifactContent", content)),
            Services.getArtifactsService().getArtifactRules(artifactId).then(rules => this.setSingleState("rules", rules)),
            Services.getArtifactsService().getArtifactVersions(artifactId).then(versions => this.setSingleState("versions", versions))
        ]).then( () => {
            this.setSingleState("isLoading", false);
        }).catch( error => {
            this.handleServerError(error, "Error loading artifact information.");
        });
    }

    private version(): string {
        return this.getPathParam("version");
    }

    private handleTabClick = (event: any, tabIndex: any): void => {
        this.setSingleState("activeTabKey", tabIndex);
    };

    private onUploadVersion = (): void => {
        this.setSingleState("isUploadModalOpen", true);
    };

    private showDocumentationTab(): boolean {
        if (this.state.artifact) {
            return this.state.artifact.type === "OPENAPI";
        } else {
            return false;
        }
    }

    private rules(): Rule[] {
        return this.state.rules ? this.state.rules : [];
    }

    private doEnableRule = (ruleType: string): void => {
        Services.getLoggerService().debug("[ArtifactVersionPage] Enabling rule:", ruleType);
        let config: string = "FULL";
        if (ruleType === "COMPATIBILITY") {
            config = "BACKWARD";
        }
        Services.getGlobalsService().updateRule(ruleType, config).catch(error => {
            this.handleServerError(error, `Error enabling "${ ruleType }" artifact rule.`);
        });
        this.setSingleState("rules", [...this.rules(), Rule.create(ruleType, config)])
    };

    private doDisableRule = (ruleType: string): void => {
        Services.getLoggerService().debug("[ArtifactVersionPage] Disabling rule:", ruleType);
        Services.getGlobalsService().updateRule(ruleType, null).catch(error => {
            this.handleServerError(error, `Error disabling "${ ruleType }" artifact rule.`);
        });
        this.setSingleState("rules", this.rules().filter(r=>r.type !== ruleType));
    };

    private doConfigureRule = (ruleType: string, config: string): void => {
        Services.getLoggerService().debug("[ArtifactVersionPage] Configuring rule:", ruleType, config);
        Services.getGlobalsService().updateRule(ruleType, config).catch(error => {
            this.handleServerError(error, `Error configuring "${ ruleType }" artifact rule.`);
        });
        this.setSingleState("rules", this.rules().map(r => {
            if (r.type === ruleType) {
                return Rule.create(r.type, config);
            } else {
                return r;
            }
        }));
    };

    private versions(): VersionMetaData[] {
        return this.state.versions ? this.state.versions : [];
    }

    private artifactId(): string {
        return this.state.artifact ? this.state.artifact.id : "";
    }

    private artifactType(): string {
        return this.state.artifact ? this.state.artifact.type : "";
    }

    private onUploadFormValid = (isValid: boolean): void => {
        this.setSingleState("isUploadFormValid", isValid);
    };

    private onUploadFormChange = (data: string): void => {
        this.setSingleState("uploadFormData", data);
    };

    private onUploadArtifact = (): void => {
        this.setSingleState("isUploadModalOpen", true);
    };

    private onUploadModalClose = (): void => {
        this.setSingleState("isUploadModalOpen", false);
    };

    private doUploadArtifact = (): void => {
        this.onUploadModalClose();
        if (this.state.uploadFormData !== null) {
            const artifactId: string = this.artifactId();
            const data: CreateVersionData = {
                content: this.state.artifactContent,
                type: this.artifactType()
            };
            Services.getArtifactsService().createArtifactVersion(artifactId, data).then(versionMetaData => {
                const artifactVersionLocation: string = `/artifacts/${ versionMetaData.id }/versions/${versionMetaData.version}`;
                Services.getLoggerService().info("Artifact version successfully uploaded.  Redirecting to details: ", artifactVersionLocation);
                this.navigateTo(artifactVersionLocation)();
            }).catch( error => {
                this.handleServerError(error, "Error uploading artifact version.");
            });
        }
    };

}
