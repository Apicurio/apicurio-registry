import { FunctionComponent, useEffect, useState } from "react";
import "./ArtifactVersionPage.css";
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
import { Link, useParams } from "react-router-dom";
import { ArtifactMetaData } from "@models/artifactMetaData.model.ts";
import { Rule } from "@models/rule.model.ts";
import { SearchedVersion } from "@models/searchedVersion.model.ts";
import {
    ArtifactVersionPageHeader,
    ContentTabContent,
    DocumentationTabContent,
    EditMetaDataModal,
    InfoTabContent,
    PageDataLoader,
    PageError,
    PageErrorHandler,
    toPageError,
    UploadVersionForm
} from "@app/pages";
import { Services } from "@services/services.ts";
import { ReferencesTabContent } from "@app/pages/artifactVersion/components/tabs/ReferencesTabContent.tsx";
import { IfFeature, InvalidContentModal, PleaseWaitModal } from "@app/components";
import { ChangeOwnerModal } from "@app/pages/artifactVersion/components/modals/ChangeOwnerModal.tsx";
import { ContentTypes } from "@models/contentTypes.model.ts";
import { ArtifactTypes } from "@models/artifactTypes.model.ts";
import { CreateVersionData, EditableMetaData } from "@services/groups";
import { AppNavigation, useAppNavigation } from "@hooks/useAppNavigation.ts";
import { ApiError } from "@models/apiError.model.ts";


export type ArtifactVersionPageProps = {
    // No properties
}

const EMPTY_ARTIFACT_META_DATA: ArtifactMetaData = {
    groupId: null,
    id: "",
    name: "",
    description: "",
    labels: [],
    properties: {},
    type: "",
    version: "",
    createdBy: "",
    createdOn: "",
    modifiedBy: "",
    modifiedOn: "",
    globalId: 1,
    contentId: 1,
    state: ""
};

/**
 * The artifact version page.
 */
export const ArtifactVersionPage: FunctionComponent<ArtifactVersionPageProps> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [activeTabKey, setActiveTabKey] = useState("overview");
    const [artifact, setArtifact] = useState<ArtifactMetaData>(EMPTY_ARTIFACT_META_DATA);
    const [artifactContent, setArtifactContent] = useState("");
    const [invalidContentError, setInvalidContentError] = useState<ApiError>();
    const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [isChangeOwnerModalOpen, setIsChangeOwnerModalOpen] = useState(false);
    const [isInvalidContentModalOpen, setIsInvalidContentModalOpen] = useState(false);
    const [isPleaseWaitModalOpen, setIsPleaseWaitModalOpen] = useState(false);
    const [isUploadFormValid, setIsUploadFormValid] = useState(false);
    const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");
    const [rules, setRules] = useState<Rule[]>([]);
    const [uploadFormData, setUploadFormData] = useState<string | null>();
    const [versions, setVersions] = useState<SearchedVersion[]>([]);

    const appNavigation: AppNavigation = useAppNavigation();
    const { groupId, artifactId, version }= useParams();

    const is404 = (e: any) => {
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
    };

    const createLoaders = (): Promise<any>[] => {
        let gid: string|null = groupId as string;
        if (gid == "default") {
            gid = null;
        }
        Services.getLoggerService().info("Loading data for artifact: ", artifactId);
        return [
            Services.getGroupsService().getArtifactMetaData(gid, artifactId as string, version as string)
                .then(setArtifact)
                .catch(error => {
                    setPageError(toPageError(error, "Error loading page data."));
                }),
            Services.getGroupsService().getArtifactContent(gid, artifactId as string, version as string)
                .then(setArtifactContent)
                .catch(e => {
                    Services.getLoggerService().warn("Failed to get artifact content: ", e);
                    if (is404(e)) {
                        setArtifactContent("Artifact version content not available (404 Not Found).");
                    } else {
                        const pageError: PageError = toPageError(e, "Error loading page data.");
                        setPageError(pageError);
                    }
                }),
            Services.getGroupsService().getArtifactRules(gid, artifactId as string)
                .then(setRules)
                .catch(error => {
                    setPageError(toPageError(error, "Error loading page data."));
                }),
            Services.getGroupsService().getArtifactVersions(gid, artifactId as string)
                .then(versions => {
                    setVersions(versions.reverse());
                })
                .catch(error => {
                    setPageError(toPageError(error, "Error loading page data."));
                })
        ];
    };

    const handleTabClick = (_event: any, tabIndex: any): void => {
        setActiveTabKey(tabIndex);
    };

    const onUploadVersion = (): void => {
        setIsUploadModalOpen(true);
    };

    const onDeleteArtifact = (): void => {
        setIsDeleteModalOpen(true);
    };

    const showDocumentationTab = (): boolean => {
        if (artifact) {
            // return (artifact.type === "OPENAPI" || artifact.type === "ASYNCAPI") && artifact.state !== "DISABLED";
            return artifact.type === "OPENAPI" && artifact.state !== "DISABLED";
        } else {
            return false;
        }
    };

    const doEnableRule = (ruleType: string): void => {
        Services.getLoggerService().debug("[ArtifactVersionPage] Enabling rule:", ruleType);
        let config: string = "FULL";
        if (ruleType === "COMPATIBILITY") {
            config = "BACKWARD";
        }
        Services.getGroupsService().createArtifactRule(groupId as string, artifactId as string, ruleType, config).catch(error => {
            setPageError(toPageError(error, `Error enabling "${ ruleType }" artifact rule.`));
        });
        setRules([...rules, { config, type: ruleType }]);
    };

    const doDisableRule = (ruleType: string): void => {
        Services.getLoggerService().debug("[ArtifactVersionPage] Disabling rule:", ruleType);
        Services.getGroupsService().deleteArtifactRule(groupId as string, artifactId as string, ruleType).catch(error => {
            setPageError(toPageError(error, `Error disabling "${ ruleType }" artifact rule.`));
        });
        setRules(rules.filter(r => r.type !== ruleType));
    };

    const doConfigureRule = (ruleType: string, config: string): void => {
        Services.getLoggerService().debug("[ArtifactVersionPage] Configuring rule:", ruleType, config);
        Services.getGroupsService().updateArtifactRule(groupId as string, artifactId as string, ruleType, config).catch(error => {
            setPageError(toPageError(error, `Error configuring "${ ruleType }" artifact rule.`));
        });
        setRules(rules.map(r => {
            if (r.type === ruleType) {
                return { config, type: r.type };
            } else {
                return r;
            }
        }));
    };

    const doDownloadArtifact = (): void => {
        const content: string = artifactContent;

        let contentType: string = ContentTypes.APPLICATION_JSON;
        let fext: string = "json";
        if (artifact?.type === ArtifactTypes.PROTOBUF) {
            contentType = ContentTypes.APPLICATION_PROTOBUF;
            fext = "proto";
        }
        if (artifact?.type === ArtifactTypes.WSDL) {
            contentType = ContentTypes.APPLICATION_XML;
            fext = "wsdl";
        }
        if (artifact?.type === ArtifactTypes.XSD) {
            contentType = ContentTypes.APPLICATION_XML;
            fext = "xsd";
        }
        if (artifact?.type === ArtifactTypes.XML) {
            contentType = ContentTypes.APPLICATION_XML;
            fext = "xml";
        }
        if (artifact?.type === ArtifactTypes.GRAPHQL) {
            contentType = ContentTypes.APPLICATION_JSON;
            fext = "graphql";
        }

        const fname: string = nameOrId() + "." + fext;
        Services.getDownloaderService().downloadToFS(content, contentType, fname).catch(error => {
            setPageError(toPageError(error, "Error downloading artifact content."));
        });
    };

    const nameOrId = (): string => {
        return artifact?.name || artifact?.id || "";
    };

    const artifactType = (): string => {
        return artifact?.type || "";
    };

    const artifactName = (): string => {
        return artifact?.name || "";
    };

    const artifactDescription = (): string => {
        return artifact?.description || "";
    };

    const artifactLabels = (): string[] => {
        return artifact?.labels || [];
    };

    const artifactProperties = (): { [key: string]: string } => {
        return artifact?.properties || {};
    };

    const onUploadFormValid = (isValid: boolean): void => {
        setIsUploadFormValid(isValid);
    };

    const onUploadFormChange = (data: string): void => {
        setUploadFormData(data);
    };

    const onUploadModalClose = (): void => {
        setIsUploadModalOpen(false);
    };

    const onDeleteModalClose = (): void => {
        setIsDeleteModalOpen(false);
    };

    const doUploadArtifactVersion = (): void => {
        onUploadModalClose();
        pleaseWait(true, "Uploading new version, please wait...");
        if (uploadFormData !== null) {
            const data: CreateVersionData = {
                content: uploadFormData as string,
                type: artifactType()
            };
            Services.getGroupsService().createArtifactVersion(groupId as string, artifactId as string, data).then(versionMetaData => {
                const groupId: string = versionMetaData.groupId ? versionMetaData.groupId : "default";
                const artifactVersionLocation: string = `/artifacts/${ encodeURIComponent(groupId) }/${ encodeURIComponent(versionMetaData.id) }/versions/${versionMetaData.version}`;
                Services.getLoggerService().info("[ArtifactVersionPage] Artifact version successfully uploaded.  Redirecting to details: ", artifactVersionLocation);
                pleaseWait(false, "");
                appNavigation.navigateTo(artifactVersionLocation);
            }).catch( error => {
                pleaseWait(false, "");
                if (error && (error.error_code === 400 || error.error_code === 409)) {
                    handleInvalidContentError(error);
                } else {
                    setPageError(toPageError(error, "Error uploading artifact version."));
                }
                setUploadFormData(null);
                setIsUploadFormValid(false);
            });
        }
    };

    const doDeleteArtifact = (): void => {
        onDeleteModalClose();
        pleaseWait(true, "Deleting artifact, please wait...");
        Services.getGroupsService().deleteArtifact(groupId as string, artifactId as string).then( () => {
            pleaseWait(false, "");
            appNavigation.navigateTo("/artifacts");
        });
    };

    const openEditMetaDataModal = (): void => {
        setIsEditModalOpen(true);
    };

    const openChangeOwnerModal = (): void => {
        setIsChangeOwnerModalOpen(true);
    };

    const onEditModalClose = (): void => {
        setIsEditModalOpen(false);
    };

    const onChangeOwnerModalClose = (): void => {
        setIsChangeOwnerModalOpen(false);
    };

    const doEditMetaData = (metaData: EditableMetaData): void => {
        Services.getGroupsService().updateArtifactMetaData(groupId as string, artifactId as string, version as string, metaData).then( () => {
            if (artifact) {
                setArtifact({
                    ...artifact,
                    ...metaData
                } as ArtifactMetaData);
            }
        }).catch( error => {
            setPageError(toPageError(error, "Error editing artifact metadata."));
        });
        onEditModalClose();
    };

    const doChangeOwner = (newOwner: string): void => {
        Services.getGroupsService().updateArtifactOwner(groupId as string, artifactId as string, newOwner).then( () => {
            if (artifact) {
                setArtifact({
                    ...artifact,
                    createdBy: newOwner
                });
            }
        }).catch( error => {
            setPageError(toPageError(error, "Error changing artifact ownership."));
        });
        onChangeOwnerModalClose();
    };

    const closeInvalidContentModal = (): void => {
        setIsInvalidContentModalOpen(false);
    };

    const pleaseWait = (isOpen: boolean, message: string): void => {
        setIsPleaseWaitModalOpen(isOpen);
        setPleaseWaitMessage(message);
    };

    const handleInvalidContentError = (error: any): void => {
        Services.getLoggerService().info("INVALID CONTENT ERROR", error);
        setInvalidContentError(error);
        setIsInvalidContentModalOpen(true);
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, [groupId, artifactId, version]);

    const tabs: any[] = [
        <Tab eventKey="overview" title="Overview" key="overview" tabContentId="tab-info">
            <InfoTabContent artifact={artifact}
                isLatest={version === "latest"}
                rules={rules}
                onEnableRule={doEnableRule}
                onDisableRule={doDisableRule}
                onConfigureRule={doConfigureRule}
                onDownloadArtifact={doDownloadArtifact}
                onEditMetaData={openEditMetaDataModal}
                onChangeOwner={openChangeOwnerModal}
            />
        </Tab>,
        <Tab eventKey="documentation" title="Documentation" key="documentation" className="documentation-tab">
            <DocumentationTabContent artifactContent={artifactContent} artifactType={artifact.type} />
        </Tab>,
        <Tab eventKey="content" title="Content" key="content">
            <ContentTabContent artifactContent={artifactContent} artifactType={artifact.type} />
        </Tab>,
        <Tab eventKey="references" title="References" key="references">
            <ReferencesTabContent artifact={artifact} />
        </Tab>,
    ];
    if (!showDocumentationTab()) {
        tabs.splice(1, 1);
    }

    const gid: string = groupId || "default";
    const hasGroup: boolean = gid != "default";
    let breadcrumbs = (
        <Breadcrumb>
            <BreadcrumbItem><Link to={appNavigation.createLink("/artifacts")} data-testid="breadcrumb-lnk-artifacts">Artifacts</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/artifacts?group=${ encodeURIComponent(gid) }`)}
                data-testid="breadcrumb-lnk-group">{ gid }</Link></BreadcrumbItem>
            <BreadcrumbItem isActive={true}>{ artifactId as string }</BreadcrumbItem>
        </Breadcrumb>
    );
    if (!hasGroup) {
        breadcrumbs = (
            <Breadcrumb>
                <BreadcrumbItem><Link to="/artifacts" data-testid="breadcrumb-lnk-artifacts">Artifacts</Link></BreadcrumbItem>
                <BreadcrumbItem isActive={true}>{ artifactId as string }</BreadcrumbItem>
            </Breadcrumb>
        );
    }

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <IfFeature feature="breadcrumbs" is={true}>
                    <PageSection className="ps_header-breadcrumbs" variant={PageSectionVariants.light} children={breadcrumbs} />
                </IfFeature>
                <PageSection className="ps_artifact-version-header" variant={PageSectionVariants.light}>
                    <ArtifactVersionPageHeader title={nameOrId()}
                        versions={versions}
                        version={version as string}
                        onUploadVersion={onUploadVersion}
                        onDeleteArtifact={onDeleteArtifact}
                        groupId={gid}
                        artifactId={artifactId as string} />
                </PageSection>
                <PageSection variant={PageSectionVariants.light} isFilled={true} padding={{ default: "noPadding" }} className="artifact-details-main">
                    <Tabs className="artifact-page-tabs"
                        id="artifact-page-tabs"
                        unmountOnExit={true}
                        isFilled={false}
                        activeKey={activeTabKey}
                        children={tabs}
                        onSelect={handleTabClick}
                    />
                </PageSection>
            </PageDataLoader>
            <Modal
                title="Upload Artifact Version"
                variant="large"
                isOpen={isUploadModalOpen}
                onClose={onUploadModalClose}
                className="upload-artifact-modal pf-m-redhat-font"
                actions={[
                    <Button key="upload" variant="primary" data-testid="modal-btn-upload" onClick={doUploadArtifactVersion} isDisabled={!isUploadFormValid}>Upload</Button>,
                    <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={onUploadModalClose}>Cancel</Button>
                ]}
            >
                <UploadVersionForm onChange={onUploadFormChange} onValid={onUploadFormValid} />
            </Modal>
            <Modal
                title="Delete Artifact"
                variant="small"
                isOpen={isDeleteModalOpen}
                onClose={onDeleteModalClose}
                className="delete-artifact-modal pf-m-redhat-font"
                actions={[
                    <Button key="delete" variant="primary" data-testid="modal-btn-delete" onClick={doDeleteArtifact}>Delete</Button>,
                    <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={onDeleteModalClose}>Cancel</Button>
                ]}
            >
                <p>Do you want to delete this artifact and all of its versions?  This action cannot be undone.</p>
            </Modal>
            <EditMetaDataModal name={artifactName()}
                description={artifactDescription()}
                labels={artifactLabels()}
                properties={artifactProperties()}
                isOpen={isEditModalOpen}
                onClose={onEditModalClose}
                onEditMetaData={doEditMetaData}
            />
            <ChangeOwnerModal isOpen={isChangeOwnerModalOpen}
                onClose={onChangeOwnerModalClose}
                currentOwner={artifact?.createdBy || ""}
                onChangeOwner={doChangeOwner}
            />
            <InvalidContentModal error={invalidContentError}
                isOpen={isInvalidContentModalOpen}
                onClose={closeInvalidContentModal} />
            <PleaseWaitModal message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen} />
        </PageErrorHandler>
    );

};
