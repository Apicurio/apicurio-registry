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
    Breadcrumb,
    BreadcrumbItem,
    Button,
    Modal,
    PageSection,
    PageSectionVariants,
    Tab,
    Tabs
} from "@patternfly/react-core";
import { PageComponent, PageProps, PageState } from "../basePage";
import { ContentTabContent, DocumentationTabContent, InfoTabContent } from "./components/tabs";
import { ArtifactVersionPageHeader } from "./components/pageheader";
import { UploadVersionForm } from "./components/uploadForm";
import { Link } from "react-router-dom";
import { EditMetaDataModal } from "./components/modals";
import { InvalidContentModal } from "../../components/modals";
import { IfFeature } from "../../components";
import { ArtifactMetaData, ArtifactTypes, ContentTypes, Rule, SearchedVersion } from "../../../models";
import { CreateVersionData, EditableMetaData, Services } from "../../../services";
import { PleaseWaitModal } from "../../components/modals/pleaseWaitModal";
import { ChangeOwnerModal } from "./components/modals/changeOwnerModal";


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
    isInvalidContentModalOpen: boolean;
    isUploadFormValid: boolean;
    isUploadModalOpen: boolean;
    isDeleteModalOpen: boolean;
    isEditModalOpen: boolean;
    isChangeOwnerModalOpen: boolean;
    isPleaseWaitModalOpen: boolean;
    pleaseWaitMessage: string;
    rules: Rule[] | null;
    uploadFormData: string | null;
    versions: SearchedVersion[] | null;
    invalidContentError: any | null;
}

function is404(e: any) {
    if (typeof e === "string") {
        try {
            const eo: any = JSON.parse(e);
            if (eo && eo.error_code && eo.error_code === 404) {
                return true;
            }
        } catch (e) {
            // Do nothing
        }
    }
    return false;
}

/**
 * The artifact version page.
 */
export class ArtifactVersionPage extends PageComponent<ArtifactVersionPageProps, ArtifactVersionPageState> {

    constructor(props: Readonly<ArtifactVersionPageProps>) {
        super(props);
    }

    public renderPage(): React.ReactElement {
        const artifact: ArtifactMetaData = this.state.artifact ? this.state.artifact : new ArtifactMetaData();
        const tabs: React.ReactNode[] = [
            <Tab eventKey={0} title="Overview" key="overview" tabContentId="tab-info">
                <InfoTabContent artifact={artifact}
                                isLatest={this.versionParam() === "latest"}
                                rules={this.rules()}
                                onEnableRule={this.doEnableRule}
                                onDisableRule={this.doDisableRule}
                                onConfigureRule={this.doConfigureRule}
                                onDownloadArtifact={this.doDownloadArtifact}
                                onEditMetaData={this.openEditMetaDataModal}
                                onChangeOwner={this.openChangeOwnerModal}
                />
            </Tab>,
            <Tab eventKey={1} title="Documentation" key="documentation">
                <DocumentationTabContent artifactContent={this.state.artifactContent} artifactType={artifact.type} />
            </Tab>,
            <Tab eventKey={2} title="Content" key="content">
                <ContentTabContent artifactContent={this.state.artifactContent} artifactType={artifact.type} />
            </Tab>,
        ];
        if (!this.showDocumentationTab()) {
            tabs.splice(1, 1);
        }

        let groupId: string = this.groupIdParam();
        let hasGroup: boolean = groupId != "default";
        let breadcrumbs = (
            <Breadcrumb>
                <BreadcrumbItem><Link to={this.linkTo("/artifacts")} data-testid="breadcrumb-lnk-artifacts">Artifacts</Link></BreadcrumbItem>
                <BreadcrumbItem><Link to={this.linkTo(`/artifacts?group=${ encodeURIComponent(groupId) }`)}
                                      data-testid="breadcrumb-lnk-group">{ groupId }</Link></BreadcrumbItem>
                <BreadcrumbItem isActive={true}>{ this.artifactId() }</BreadcrumbItem>
            </Breadcrumb>
        );
        if (!hasGroup) {
            breadcrumbs = (
                <Breadcrumb>
                    <BreadcrumbItem><Link to="/artifacts" data-testid="breadcrumb-lnk-artifacts">Artifacts</Link></BreadcrumbItem>
                    <BreadcrumbItem isActive={true}>{ this.artifactId() }</BreadcrumbItem>
                </Breadcrumb>
            );
        }

        return (
            <React.Fragment>
                <IfFeature feature="breadcrumbs" is={true}>
                    <PageSection className="ps_header-breadcrumbs" variant={PageSectionVariants.light} children={breadcrumbs} />
                </IfFeature>
                <PageSection className="ps_artifact-version-header" variant={PageSectionVariants.light}>
                    <ArtifactVersionPageHeader title={this.nameOrId()}
                                               versions={this.versions()}
                                               version={this.versionParam()}
                                               onUploadVersion={this.onUploadVersion}
                                               onDeleteArtifact={this.onDeleteArtifact}
                                               groupId={groupId}
                                               artifactId={this.artifactId()} />
                </PageSection>
                <PageSection variant={PageSectionVariants.light} isFilled={true} padding={{default : "noPadding"}} className="artifact-details-main">
                    <Tabs className="artifact-page-tabs"
                          id="artifact-page-tabs"
                          unmountOnExit={true}
                          isFilled={false}
                          activeKey={this.state.activeTabKey}
                          children={tabs}
                          onSelect={this.handleTabClick}
                    />
                </PageSection>
                <Modal
                    title="Upload Artifact Version"
                    variant="large"
                    isOpen={this.state.isUploadModalOpen}
                    onClose={this.onUploadModalClose}
                    className="upload-artifact-modal pf-m-redhat-font"
                    actions={[
                        <Button key="upload" variant="primary" data-testid="modal-btn-upload" onClick={this.doUploadArtifactVersion} isDisabled={!this.state.isUploadFormValid}>Upload</Button>,
                        <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.onUploadModalClose}>Cancel</Button>
                    ]}
                >
                    <UploadVersionForm onChange={this.onUploadFormChange} onValid={this.onUploadFormValid} />
                </Modal>
                <Modal
                    title="Delete Artifact"
                    variant="small"
                    isOpen={this.state.isDeleteModalOpen}
                    onClose={this.onDeleteModalClose}
                    className="delete-artifact-modal pf-m-redhat-font"
                    actions={[
                        <Button key="delete" variant="primary" data-testid="modal-btn-delete" onClick={this.doDeleteArtifact}>Delete</Button>,
                        <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.onDeleteModalClose}>Cancel</Button>
                    ]}
                >
                    <p>Do you want to delete this artifact and all of its versions?  This action cannot be undone.</p>
                </Modal>
                <EditMetaDataModal name={this.artifactName()}
                                   description={this.artifactDescription()}
                                   labels={this.artifactLabels()}
                                   properties={this.artifactProperties()}
                                   isOpen={this.state.isEditModalOpen}
                                   onClose={this.onEditModalClose}
                                   onEditMetaData={this.doEditMetaData}
                />
                <ChangeOwnerModal isOpen={this.state.isChangeOwnerModalOpen}
                                  onClose={this.onChangeOwnerModalClose}
                                  currentOwner={this.state.artifact?.createdBy || ""}
                                  onChangeOwner={this.doChangeOwner}
                />
                <InvalidContentModal error={this.state.invalidContentError}
                                     isOpen={this.state.isInvalidContentModalOpen}
                                     onClose={this.closeInvalidContentModal} />
                <PleaseWaitModal message={this.state.pleaseWaitMessage}
                                 isOpen={this.state.isPleaseWaitModalOpen} />
            </React.Fragment>
        );
    }

    protected initializePageState(): ArtifactVersionPageState {
        return {
            activeTabKey: 0,
            artifact: null,
            artifactContent: "",
            artifactIsText: true,
            invalidContentError: null,
            isDeleteModalOpen: false,
            isEditModalOpen: false,
            isChangeOwnerModalOpen: false,
            isInvalidContentModalOpen: false,
            isPleaseWaitModalOpen: false,
            isUploadFormValid: false,
            isUploadModalOpen: false,
            pleaseWaitMessage: "",
            rules: null,
            uploadFormData: null,
            versions: null
        };
    }

    protected groupIdParam(): string {
        return this.getPathParam("groupId");
    }

    protected artifactIdParam(): string {
        return this.getPathParam("artifactId");
    }

    protected versionParam(): string {
        return this.getPathParam("version");
    }

    // @ts-ignore
    protected createLoaders(): Promise[] | null {
        let groupId: string|null = this.groupIdParam();
        if (groupId == "default") {
            groupId = null;
        }
        const artifactId: string = this.artifactIdParam();
        Services.getLoggerService().info("Loading data for artifact: ", artifactId);
        return [
            Services.getGroupsService().getArtifactMetaData(groupId, artifactId, this.versionParam()).then(md => this.setSingleState("artifact", md)),
            Services.getGroupsService().getArtifactContent(groupId, artifactId, this.versionParam())
                .then(content => this.setSingleState("artifactContent", content))
                .catch(e => {
                    Services.getLoggerService().warn("Failed to get artifact content: ", e);
                    if (is404(e)) {
                        this.setSingleState("artifactContent", "Artifact version content not available (404 Not Found).");
                    } else {
                        throw e;
                    }
                }
            ),
            Services.getGroupsService().getArtifactRules(groupId, artifactId).then(rules => this.setSingleState("rules", rules)),
            Services.getGroupsService().getArtifactVersions(groupId, artifactId).then(versions => this.setSingleState("versions", versions.reverse()))
        ];
    }

    private handleTabClick = (event: any, tabIndex: any): void => {
        this.setSingleState("activeTabKey", tabIndex);
    };

    private onUploadVersion = (): void => {
        this.setSingleState("isUploadModalOpen", true);
    };

    private onDeleteArtifact = (): void => {
        this.setSingleState("isDeleteModalOpen", true);
    };

    private showDocumentationTab(): boolean {
        if (this.state.artifact) {
            return (this.state.artifact.type === "OPENAPI" || this.state.artifact.type === "ASYNCAPI") && this.state.artifact.state !== "DISABLED";
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
        Services.getGroupsService().createArtifactRule(this.groupId(), this.artifactId(), ruleType, config).catch(error => {
            this.handleServerError(error, `Error enabling "${ ruleType }" artifact rule.`);
        });
        this.setSingleState("rules", [...this.rules(), {config, type: ruleType}]);
    };

    private doDisableRule = (ruleType: string): void => {
        Services.getLoggerService().debug("[ArtifactVersionPage] Disabling rule:", ruleType);
        Services.getGroupsService().deleteArtifactRule(this.groupId(), this.artifactId(), ruleType).catch(error => {
            this.handleServerError(error, `Error disabling "${ ruleType }" artifact rule.`);
        });
        this.setSingleState("rules", this.rules().filter(r => r.type !== ruleType));
    };

    private doConfigureRule = (ruleType: string, config: string): void => {
        Services.getLoggerService().debug("[ArtifactVersionPage] Configuring rule:", ruleType, config);
        Services.getGroupsService().updateArtifactRule(this.groupId(), this.artifactId(), ruleType, config).catch(error => {
            this.handleServerError(error, `Error configuring "${ ruleType }" artifact rule.`);
        });
        this.setSingleState("rules", this.rules().map(r => {
            if (r.type === ruleType) {
                return {config, type: r.type};
            } else {
                return r;
            }
        }));
    };

    private doDownloadArtifact = (): void => {
        const content: string = this.state.artifactContent;

        let contentType: string = ContentTypes.APPLICATION_JSON;
        let fext: string = "json";
        if (this.state.artifact?.type === ArtifactTypes.PROTOBUF) {
            contentType = ContentTypes.APPLICATION_PROTOBUF;
            fext = "proto";
        }
        if (this.state.artifact?.type === ArtifactTypes.WSDL) {
            contentType = ContentTypes.APPLICATION_XML;
            fext = "wsdl";
        }
        if (this.state.artifact?.type === ArtifactTypes.XSD) {
            contentType = ContentTypes.APPLICATION_XML;
            fext = "xsd";
        }
        if (this.state.artifact?.type === ArtifactTypes.XML) {
            contentType = ContentTypes.APPLICATION_XML;
            fext = "xml";
        }
        if (this.state.artifact?.type === ArtifactTypes.GRAPHQL) {
            contentType = ContentTypes.APPLICATION_JSON;
            fext = "graphql";
        }

        const fname: string = this.artifactNameOrId() + "." + fext;
        Services.getDownloaderService().downloadToFS(content, contentType, fname);
    };

    private nameOrId(): string {
        if (!this.state.artifact) {
            return "";
        }
        return this.state.artifact.name ? this.state.artifact.name : this.state.artifact.id;
    }

    private versions(): SearchedVersion[] {
        return this.state.versions ? this.state.versions : [];
    }

    private artifactId(): string {
        return this.state.artifact ? this.state.artifact.id : "";
    }

    private groupId(): string|null {
        return this.state.artifact ? this.state.artifact.groupId : null;
    }

    private artifactType(): string {
        return this.state.artifact ? this.state.artifact.type : "";
    }

    private artifactNameOrId(): string {
        return this.state.artifact ? (
            this.state.artifact.name ? this.state.artifact.name : this.state.artifact.id
        ) : "";
    }

    private artifactName(): string {
        return this.state.artifact ? (
            this.state.artifact.name ? this.state.artifact.name : ""
        ) : "";
    }

    private artifactDescription(): string {
        return this.state.artifact ? (
            this.state.artifact.description ? this.state.artifact.description : ""
        ) : "";
    }

    private artifactLabels(): string[] {
        return this.state.artifact ? (
            this.state.artifact.labels ? this.state.artifact.labels : []
        ) : [];
    }

    private artifactProperties(): { [key: string]: string } {
        return this.state.artifact?.properties || {};
    }

    private onUploadFormValid = (isValid: boolean): void => {
        this.setSingleState("isUploadFormValid", isValid);
    };

    private onUploadFormChange = (data: string): void => {
        this.setSingleState("uploadFormData", data);
    };

    private onUploadModalClose = (): void => {
        this.setSingleState("isUploadModalOpen", false);
    };

    private onDeleteModalClose = (): void => {
        this.setSingleState("isDeleteModalOpen", false);
    };

    private doUploadArtifactVersion = (): void => {
        this.onUploadModalClose();
        this.pleaseWait(true, "Uploading new version, please wait...");
        if (this.state.uploadFormData !== null) {
            const data: CreateVersionData = {
                content: this.state.uploadFormData,
                type: this.artifactType()
            };
            Services.getGroupsService().createArtifactVersion(this.groupId(), this.artifactId(), data).then(versionMetaData => {
                const groupId: string = versionMetaData.groupId ? versionMetaData.groupId : "default";
                const artifactVersionLocation: string = `/artifacts/${ encodeURIComponent(groupId) }/${ encodeURIComponent(versionMetaData.id) }/versions/${versionMetaData.version}`;
                Services.getLoggerService().info("[ArtifactVersionPage] Artifact version successfully uploaded.  Redirecting to details: ", artifactVersionLocation);
                this.navigateTo(this.linkTo(artifactVersionLocation))();
            }).catch( error => {
                this.pleaseWait(false, "");
                if (error && (error.error_code === 400 || error.error_code === 409)) {
                    this.handleInvalidContentError(error);
                } else {
                    this.handleServerError(error, "Error uploading artifact version.");
                }
                this.setMultiState({uploadFormData: null, isUploadFormValid: false});
            });
        }
    };

    private doDeleteArtifact = (): void => {
        this.onDeleteModalClose();
        this.pleaseWait(true, "Deleting artifact, please wait...");
        Services.getGroupsService().deleteArtifact(this.groupId(), this.artifactId()).then( () => {
            this.pleaseWait(false, "");
            this.navigateTo(this.linkTo("/artifacts"))();
        });
    };

    private openEditMetaDataModal = (): void => {
        this.setSingleState("isEditModalOpen", true);
    };

    private openChangeOwnerModal = (): void => {
        this.setSingleState("isChangeOwnerModalOpen", true);
    };

    private onEditModalClose = (): void => {
        this.setSingleState("isEditModalOpen", false);
    };

    private onChangeOwnerModalClose = (): void => {
        this.setSingleState("isChangeOwnerModalOpen", false);
    };

    private doEditMetaData = (metaData: EditableMetaData): void => {
        Services.getGroupsService().updateArtifactMetaData(this.groupId(), this.artifactId(), this.versionParam(), metaData).then( () => {
            if (this.state.artifact) {
                this.setSingleState("artifact", {
                    ...this.state.artifact,
                    ...metaData
                });
            }
        }).catch( error => {
            this.handleServerError(error, "Error editing artifact metadata.");
        });
        this.onEditModalClose();
    };

    private doChangeOwner = (newOwner: string): void => {
        Services.getGroupsService().updateArtifactOwner(this.groupId(), this.artifactId(), newOwner).then( () => {
            if (this.state.artifact) {
                this.setSingleState("artifact", {
                    ...this.state.artifact,
                    createdBy: newOwner
                });
            }
        }).catch( error => {
            this.handleServerError(error, "Error changing artifact ownership.");
        });
        this.onChangeOwnerModalClose();
    };

    private closeInvalidContentModal = (): void => {
        this.setSingleState("isInvalidContentModalOpen", false);
    };

    private pleaseWait = (isOpen: boolean, message: string): void => {
        this.setMultiState({
            isPleaseWaitModalOpen: isOpen,
            pleaseWaitMessage: message
        });
    };

    private handleInvalidContentError(error: any): void {
        Services.getLoggerService().info("INVALID CONTENT ERROR", error);
        this.setMultiState({
            invalidContentError: error,
            isInvalidContentModalOpen: true
        });
    }

}
