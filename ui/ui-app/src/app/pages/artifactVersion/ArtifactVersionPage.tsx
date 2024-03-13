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
import { ReferencesTabContent } from "@app/pages/artifactVersion/components/tabs/ReferencesTabContent.tsx";
import { IfFeature, InvalidContentModal } from "@app/components";
import { ChangeOwnerModal } from "@app/pages/artifactVersion/components/modals/ChangeOwnerModal.tsx";
import { ContentTypes } from "@models/contentTypes.model.ts";
import { ApiError } from "@models/apiError.model.ts";
import { PleaseWaitModal } from "@apicurio/common-ui-components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { CreateVersionData, EditableMetaData, GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { DownloadService, useDownloadService } from "@services/useDownloadService.ts";
import { ArtifactTypes } from "@services/useArtifactTypesService.ts";
import { VersionMetaData } from "@models/versionMetaData.model.ts";


export type ArtifactVersionPageProps = {
    // No properties
}

/**
 * The artifact version page.
 */
export const ArtifactVersionPage: FunctionComponent<ArtifactVersionPageProps> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [activeTabKey, setActiveTabKey] = useState("overview");
    const [artifact, setArtifact] = useState<ArtifactMetaData>();
    const [artifactVersion, setArtifactVersion] = useState<VersionMetaData>();
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
    const logger: LoggerService = useLoggerService();
    const groups: GroupsService = useGroupsService();
    const download: DownloadService = useDownloadService();
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
        logger.info("Loading data for artifact: ", artifactId);
        return [
            groups.getArtifactMetaData(gid, artifactId as string)
                .then(setArtifact)
                .catch(error => {
                    setPageError(toPageError(error, "Error loading page data."));
                }),
            groups.getArtifactVersionMetaData(gid, artifactId as string, version as string)
                .then(setArtifactVersion)
                .catch(error => {
                    setPageError(toPageError(error, "Error loading page data."));
                }),
            groups.getArtifactVersionContent(gid, artifactId as string, version as string)
                .then(setArtifactContent)
                .catch(e => {
                    logger.warn("Failed to get artifact content: ", e);
                    if (is404(e)) {
                        setArtifactContent("Artifact version content not available (404 Not Found).");
                    } else {
                        const pageError: PageError = toPageError(e, "Error loading page data.");
                        setPageError(pageError);
                    }
                }),
            groups.getArtifactRules(gid, artifactId as string)
                .then(setRules)
                .catch(error => {
                    setPageError(toPageError(error, "Error loading page data."));
                }),
            groups.getArtifactVersions(gid, artifactId as string)
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
        return artifact?.type === "OPENAPI" && artifactVersion?.state !== "DISABLED";
    };

    const doEnableRule = (ruleType: string): void => {
        logger.debug("[ArtifactVersionPage] Enabling rule:", ruleType);
        let config: string = "FULL";
        if (ruleType === "COMPATIBILITY") {
            config = "BACKWARD";
        }
        groups.createArtifactRule(groupId as string, artifactId as string, ruleType, config).catch(error => {
            setPageError(toPageError(error, `Error enabling "${ ruleType }" artifact rule.`));
        });
        setRules([...rules, { config, type: ruleType }]);
    };

    const doDisableRule = (ruleType: string): void => {
        logger.debug("[ArtifactVersionPage] Disabling rule:", ruleType);
        groups.deleteArtifactRule(groupId as string, artifactId as string, ruleType).catch(error => {
            setPageError(toPageError(error, `Error disabling "${ ruleType }" artifact rule.`));
        });
        setRules(rules.filter(r => r.type !== ruleType));
    };

    const doConfigureRule = (ruleType: string, config: string): void => {
        logger.debug("[ArtifactVersionPage] Configuring rule:", ruleType, config);
        groups.updateArtifactRule(groupId as string, artifactId as string, ruleType, config).catch(error => {
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
        download.downloadToFS(content, contentType, fname).catch(error => {
            setPageError(toPageError(error, "Error downloading artifact content."));
        });
    };

    const nameOrId = (): string => {
        return artifact?.name || artifact?.artifactId || "";
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

    const artifactLabels = (): { [key: string]: string } => {
        return artifact?.labels || {};
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
            groups.createArtifactVersion(groupId as string, artifactId as string, data).then(versionMetaData => {
                const groupId: string = versionMetaData.groupId ? versionMetaData.groupId : "default";
                const artifactVersionLocation: string = `/artifacts/${ encodeURIComponent(groupId) }/${ encodeURIComponent(versionMetaData.artifactId) }/versions/${versionMetaData.version}`;
                logger.info("[ArtifactVersionPage] Artifact version successfully uploaded.  Redirecting to details: ", artifactVersionLocation);
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
        groups.deleteArtifact(groupId as string, artifactId as string).then( () => {
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
        groups.updateArtifactVersionMetaData(groupId as string, artifactId as string, version as string, metaData).then( () => {
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
        groups.updateArtifactOwner(groupId as string, artifactId as string, newOwner).then( () => {
            if (artifact) {
                setArtifact({
                    ...artifact,
                    owner: newOwner
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
        logger.info("INVALID CONTENT ERROR", error);
        setInvalidContentError(error);
        setIsInvalidContentModalOpen(true);
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, [groupId, artifactId, version]);

    const tabs: any[] = [
        <Tab eventKey="overview" title="Overview" key="overview" tabContentId="tab-info">
            <InfoTabContent
                artifact={artifact as ArtifactMetaData}
                version={artifactVersion as VersionMetaData}
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
            <DocumentationTabContent artifactContent={artifactContent} artifactType={artifact?.type as string} />
        </Tab>,
        <Tab eventKey="content" title="Content" key="content">
            <ContentTabContent artifactContent={artifactContent} artifactType={artifact?.type as string} />
        </Tab>,
        <Tab eventKey="references" title="References" key="references">
            <ReferencesTabContent version={artifactVersion as VersionMetaData} />
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
                isOpen={isEditModalOpen}
                onClose={onEditModalClose}
                onEditMetaData={doEditMetaData}
            />
            <ChangeOwnerModal isOpen={isChangeOwnerModalOpen}
                onClose={onChangeOwnerModalClose}
                currentOwner={artifact?.owner || ""}
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
