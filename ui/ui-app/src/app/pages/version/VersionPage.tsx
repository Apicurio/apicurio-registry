import React, { FunctionComponent, useEffect, useState } from "react";
import "./VersionPage.css";
import { Breadcrumb, BreadcrumbItem, PageSection, Tab, Tabs } from "@patternfly/react-core";
import { Link, useLocation, useParams } from "react-router";
import {
    ContentTabContent,
    DocumentationTabContent,
    EXPLORE_PAGE_IDX,
    PageDataLoader,
    PageError,
    PageErrorHandler,
    PageProperties,
    toPageError,
    VersionOverviewTabContent,
    VersionPageHeader
} from "@app/pages";
import { ReferencesTabContent } from "@app/pages/version/components/tabs/ReferencesTabContent.tsx";
import {
    ChangeVersionStateModal,
    ConfirmDeleteModal,
    EditMetaDataModal,
    GenerateClientModal,
    IfFeature,
    InvalidContentModal,
    MetaData,
    RootPageHeader
} from "@app/components";
import { ContentTypes } from "@models/ContentTypes.ts";
import { PleaseWaitModal } from "@apicurio/common-ui-components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { DownloadService, useDownloadService } from "@services/useDownloadService.ts";
import { ArtifactTypes } from "@services/useArtifactTypesService.ts";
import {
    ArtifactMetaData,
    Labels,
    RuleViolationProblemDetails,
    SearchedVersion,
    VersionMetaData,
    VersionState
} from "@sdk/lib/generated-client/models";
import { DraftsService, useDraftsService } from "@services/useDraftsService.ts";
import { CreateDraft, Draft } from "@models/drafts";
import {
    ConfirmFinalizeModal,
    FinalizeDryRunSuccessModal,
    NewDraftFromModal
} from "@app/pages/drafts/components/modals";


/**
 * The artifact version page.
 */
export const VersionPage: FunctionComponent<PageProperties> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [artifact, setArtifact] = useState<ArtifactMetaData>();
    const [artifactVersion, setArtifactVersion] = useState<VersionMetaData>();
    const [draft, setDraft] = useState<Draft | undefined>();
    const [versionContent, setArtifactContent] = useState("");
    const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [isPleaseWaitModalOpen, setIsPleaseWaitModalOpen] = useState(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");
    const [isGenerateClientModalOpen, setIsGenerateClientModalOpen] = useState(false);
    const [isConfirmFinalizeModalOpen, setIsConfirmFinalizeModalOpen] = useState(false);
    const [isCreateDraftFromModalOpen, setIsCreateDraftFromModalOpen] = useState(false);
    const [isInvalidContentModalOpen, setIsInvalidContentModalOpen] = useState<boolean>(false);
    const [invalidContentError, setInvalidContentError] = useState<RuleViolationProblemDetails>();
    const [isFinalizeDryRunSuccessModalOpen, setIsFinalizeDryRunSuccessModalOpen] = useState(false);
    const [isChangeStateModalOpen, setIsChangeStateModalOpen] = useState(false);

    const appNavigation: AppNavigation = useAppNavigation();
    const logger: LoggerService = useLoggerService();
    const groups: GroupsService = useGroupsService();
    const draftsService: DraftsService = useDraftsService();
    const download: DownloadService = useDownloadService();
    const { groupId, artifactId, version }= useParams();
    const location = useLocation();

    let activeTabKey: string = "overview";
    if (location.pathname.indexOf("/content") !== -1) {
        activeTabKey = "content";
    } else if (location.pathname.indexOf("/references") !== -1) {
        activeTabKey = "references";
    } else if (location.pathname.indexOf("/documentation") !== -1) {
        activeTabKey = "documentation";
    }

    const is404 = (e: any) => {
        if (typeof e === "string") {
            try {
                const eo: any = JSON.parse(e);
                if (eo && eo.status && eo.status === 404) {
                    return true;
                }
            } catch {
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
        ];
    };

    const handleTabClick = (_event: any, tabIndex: any): void => {
        const gid: string = encodeURIComponent(groupId as string);
        const aid: string = encodeURIComponent(artifactId as string);
        const ver: string = encodeURIComponent(version as string);
        if (tabIndex === "overview") {
            appNavigation.navigateTo(`/explore/${gid}/${aid}/versions/${ver}`);
        } else {
            appNavigation.navigateTo(`/explore/${gid}/${aid}/versions/${ver}/${tabIndex}`);
        }
    };

    const onEditDraft = (): void => {
        goToDraft(groupId!, artifactId!, version!);
    };

    const goToDraft = (groupId: string, artifactId: string, version: string): void => {
        const gid: string = encodeURIComponent(groupId);
        const aid: string = encodeURIComponent(artifactId);
        const ver: string = encodeURIComponent(version);
        appNavigation.navigateTo(`/explore/${gid}/${aid}/versions/${ver}/editor`);
    };

    const onDeleteVersion = (): void => {
        setIsDeleteModalOpen(true);
    };

    const showDocumentationTab = (): boolean => {
        return artifact?.artifactType === "OPENAPI" && artifactVersion?.state !== "DISABLED";
    };

    const doDownloadVersion = (): void => {
        const content: string = versionContent;

        let contentType: string = ContentTypes.APPLICATION_JSON;
        let fext: string = "json";
        if (artifact?.artifactType === ArtifactTypes.PROTOBUF) {
            contentType = ContentTypes.APPLICATION_PROTOBUF;
            fext = "proto";
        }
        if (artifact?.artifactType === ArtifactTypes.WSDL) {
            contentType = ContentTypes.APPLICATION_XML;
            fext = "wsdl";
        }
        if (artifact?.artifactType === ArtifactTypes.XSD) {
            contentType = ContentTypes.APPLICATION_XML;
            fext = "xsd";
        }
        if (artifact?.artifactType === ArtifactTypes.XML) {
            contentType = ContentTypes.APPLICATION_XML;
            fext = "xml";
        }
        if (artifact?.artifactType === ArtifactTypes.GRAPHQL) {
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

    const versionName = (): string => {
        return artifactVersion?.name || "";
    };

    const versionDescription = (): string => {
        return artifactVersion?.description || "";
    };

    const versionLabels = (): Labels => {
        return artifactVersion?.labels || {};
    };

    const onDeleteModalClose = (): void => {
        setIsDeleteModalOpen(false);
    };

    const doDeleteVersion = (): void => {
        onDeleteModalClose();
        pleaseWait(true, "Deleting version, please wait...");
        groups.deleteArtifactVersion(groupId!, artifactId!, version!).then( () => {
            pleaseWait(false);
            const gid: string = encodeURIComponent(groupId || "default");
            const aid: string = encodeURIComponent(artifactId as string);
            appNavigation.navigateTo(`/explore/${gid}/${aid}/versions`);
        }).catch(error => {
            setPageError(toPageError(error, "Error deleting a version."));
        });
    };

    const openEditMetaDataModal = (): void => {
        setIsEditModalOpen(true);
    };

    const onEditModalClose = (): void => {
        setIsEditModalOpen(false);
    };

    const doEditMetaData = (metaData: MetaData): void => {
        groups.updateArtifactVersionMetaData(groupId as string, artifactId as string, version as string, metaData).then( () => {
            if (artifact) {
                setArtifactVersion({
                    ...artifactVersion,
                    ...metaData
                } as VersionMetaData);
            }
        }).catch( error => {
            setPageError(toPageError(error, "Error editing artifact metadata."));
        });
        onEditModalClose();
    };

    const openChangeStateModal = (): void => {
        setIsChangeStateModalOpen(true);
    };

    const onChangeStateModalClose = (): void => {
        setIsChangeStateModalOpen(false);
    };

    const doChangeState = (newState: VersionState): void => {
        onChangeStateModalClose();
        pleaseWait(true, "Changing version state, please wait...");
        groups.updateArtifactVersionState(groupId as string, artifactId as string, version as string, newState).then(() => {
            pleaseWait(false);
            setArtifactVersion({
                ...artifactVersion,
                state: newState
            } as VersionMetaData);
        }).catch(error => {
            pleaseWait(false);
            if (error && (error.status === 400 || error.status === 409)) {
                handleInvalidContentError(error);
            } else {
                setPageError(toPageError(error, "Error changing version state."));
            }
        });
    };

    const handleInvalidContentError = (error: any): void => {
        console.info("[DraftsPage] Invalid content error:", error);
        setInvalidContentError(error);
        setIsInvalidContentModalOpen(true);
    };

    const doFinalizeDraft = (draft: Draft, dryRun?: boolean): void => {
        setIsConfirmFinalizeModalOpen(false);
        pleaseWait(true, "Finalizing draft, please wait...");
        draftsService.finalizeDraft(draft.groupId, draft.draftId, draft.version, dryRun || false).then(() => {
            if (!dryRun) {
                const groupId: string = encodeURIComponent(draft.groupId || "default");
                const draftId: string = encodeURIComponent(draft.draftId!);
                appNavigation.navigateTo(`/explore/${groupId}/${draftId}`);
            } else {
                pleaseWait(false);
                setIsFinalizeDryRunSuccessModalOpen(true);
            }
        }).catch(error => {
            pleaseWait(false);
            if (error && (error.status === 400 || error.status === 409)) {
                handleInvalidContentError(error);
            } else {
                setPageError(toPageError(error, "Error finalizing a draft."));
            }
        });
    };

    const doCreateDraft = (data: CreateDraft): void => {
        pleaseWait(true, "Creating draft, please wait...");

        draftsService.createDraft(data).then(draft => {
            pleaseWait(false);
            console.info("[DraftsPage] Draft successfully created.  Redirecting to editor.");
            goToDraft(draft.groupId, draft.draftId, draft.version);
        }).catch(error => {
            pleaseWait(false);
            setPageError(toPageError(error, "Error creating draft."));
        });
    };

    const doCreateDraftFromVersion = (fromVersion: SearchedVersion, groupId: string, draftId: string, version: string): void => {
        pleaseWait(true, "Creating draft, please wait...");
        setIsCreateDraftFromModalOpen(false);

        draftsService.getDraftContent(fromVersion.groupId || null, fromVersion.artifactId!, fromVersion.version!).then(draftContent => {
            const createDraft: CreateDraft = {
                groupId: groupId,
                draftId: draftId,
                version: version,
                type: fromVersion.artifactType!,
                name: "",
                description: "",
                labels: {
                    basedOnGroupId: fromVersion.groupId,
                    basedOnArtifactId: fromVersion.artifactId,
                    basedOnVersion: fromVersion.version
                },
                content: draftContent.content,
                contentType: draftContent.contentType
            };
            doCreateDraft(createDraft);
        }).catch(error => {
            pleaseWait(false);
            setPageError(toPageError(error, "Error creating draft."));
        });
    };

    const pleaseWait = (isOpen: boolean, message: string = ""): void => {
        setIsPleaseWaitModalOpen(isOpen);
        setPleaseWaitMessage(message);
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, [groupId, artifactId, version]);

    useEffect(() => {
        if (artifactVersion && artifactVersion.state === "DRAFT") {
            const newDraft: Draft = {
                groupId: artifactVersion.groupId || "default",
                draftId: artifactVersion.artifactId!,
                version: artifactVersion.version!,
                type: artifactVersion.artifactType!,
                isDraft: true,
                contentId: artifactVersion.contentId!,
                createdBy: artifactVersion.owner!,
                createdOn: artifactVersion.createdOn!,
                modifiedBy: artifactVersion.modifiedBy!,
                modifiedOn: artifactVersion.modifiedOn!,
                name: artifactVersion.name!,
                description: artifactVersion.description!,
                labels: artifactVersion.labels!
            };
            setDraft(newDraft);
        } else {
            setDraft(undefined);
        }
    }, [artifactVersion]);

    const tabs: any[] = [
        <Tab data-testid="version-overview-tab" eventKey="overview" title="Overview" key="overview" tabContentId="tab-overview">
            <VersionOverviewTabContent
                artifact={artifact as ArtifactMetaData}
                version={artifactVersion as VersionMetaData}
                onEditMetaData={openEditMetaDataModal}
                onChangeState={openChangeStateModal}
            />
        </Tab>,
        <Tab data-testid="version-documentation-tab" eventKey="documentation" title="Documentation" key="documentation" className="documentation-tab" tabContentId="tab-documentation">
            <DocumentationTabContent versionContent={versionContent} artifactType={artifact?.artifactType as string} />
        </Tab>,
        <Tab data-testid="version-content-tab" eventKey="content" title="Content" key="content" tabContentId="tab-content">
            <ContentTabContent versionContent={versionContent} artifactType={artifact?.artifactType as string} />
        </Tab>,
        <Tab data-testid="version-references-tab" eventKey="references" title="References" key="references" tabContentId="tab-references">
            <ReferencesTabContent version={artifactVersion as VersionMetaData} />
        </Tab>,
    ];
    if (!showDocumentationTab()) {
        tabs.splice(1, 1);
    }

    const gid: string = groupId || "default";
    const breadcrumbs = (
        <Breadcrumb>
            <BreadcrumbItem><Link to={appNavigation.createLink("/explore")} data-testid="breadcrumb-lnk-explore">Explore</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ encodeURIComponent(gid) }`)}
                data-testid="breadcrumb-lnk-group">{ gid }</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ encodeURIComponent(gid) }/${ encodeURIComponent(artifactId||"") }`)}
                data-testid="breadcrumb-lnk-artifact">{ artifactId }</Link></BreadcrumbItem>
            <BreadcrumbItem isActive={true}>{ version as string }</BreadcrumbItem>
        </Breadcrumb>
    );

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection hasBodyWrapper={false} className="ps_explore-header"  padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={EXPLORE_PAGE_IDX} />
                </PageSection>
                <IfFeature feature="breadcrumbs" is={true}>
                    <PageSection hasBodyWrapper={false} className="ps_header-breadcrumbs"  children={breadcrumbs} />
                </IfFeature>
                <PageSection hasBodyWrapper={false} className="ps_artifact-version-header" >
                    <VersionPageHeader
                        onEdit={onEditDraft}
                        onDelete={onDeleteVersion}
                        onDownload={doDownloadVersion}
                        onFinalizeDraft={() => {
                            setIsConfirmFinalizeModalOpen(true);
                        }}
                        onCreateDraftFrom={() => {
                            setIsCreateDraftFromModalOpen(true);
                        }}
                        artifact={artifact}
                        version={artifactVersion}
                        codegenEnabled={true}
                        onGenerateClient={() => setIsGenerateClientModalOpen(true)}
                    />
                </PageSection>
                <PageSection hasBodyWrapper={false}  isFilled={true} padding={{ default: "noPadding" }} className="artifact-details-main">
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
            <ConfirmDeleteModal
                title="Delete Version"
                message="Do you want to delete this version?  This action cannot be undone."
                isOpen={isDeleteModalOpen}
                onDelete={doDeleteVersion}
                onClose={onDeleteModalClose} />
            <EditMetaDataModal
                entityType="version"
                name={versionName()}
                description={versionDescription()}
                labels={versionLabels()}
                isOpen={isEditModalOpen}
                onClose={onEditModalClose}
                onEditMetaData={doEditMetaData}
            />
            <GenerateClientModal
                artifactContent={versionContent}
                onClose={() => setIsGenerateClientModalOpen(false)}
                isOpen={isGenerateClientModalOpen}
            />
            <ConfirmFinalizeModal
                draft={draft}
                onClose={() => setIsConfirmFinalizeModalOpen(false)}
                onFinalize={doFinalizeDraft}
                isOpen={isConfirmFinalizeModalOpen} />
            <InvalidContentModal
                error={invalidContentError}
                isOpen={isInvalidContentModalOpen}
                onClose={() => {
                    setInvalidContentError(undefined);
                    setIsInvalidContentModalOpen(false);
                }} />
            <NewDraftFromModal
                isOpen={isCreateDraftFromModalOpen}
                onClose={() => setIsCreateDraftFromModalOpen(false)}
                onCreate={doCreateDraftFromVersion}
                fromVersion={artifactVersion!} />
            <FinalizeDryRunSuccessModal
                isOpen={isFinalizeDryRunSuccessModalOpen}
                onClose={() => setIsFinalizeDryRunSuccessModalOpen(false)} />
            <ChangeVersionStateModal
                isOpen={isChangeStateModalOpen}
                currentState={artifactVersion?.state as VersionState}
                onClose={onChangeStateModalClose}
                onChangeState={doChangeState}
            />
            <PleaseWaitModal
                message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen} />
        </PageErrorHandler>
    );

};
