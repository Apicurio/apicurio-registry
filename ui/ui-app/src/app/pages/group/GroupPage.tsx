import { FunctionComponent, useEffect, useState } from "react";
import "./GroupPage.css";
import { Breadcrumb, BreadcrumbItem, PageSection, PageSectionVariants, Tab, Tabs } from "@patternfly/react-core";
import { Link, useParams } from "react-router-dom";
import {
    GroupInfoTabContent,
    GroupPageHeader,
    PageDataLoader,
    PageError,
    PageErrorHandler,
    toPageError
} from "@app/pages";
import {
    ChangeOwnerModal,
    ConfirmDeleteModal, CreateArtifactModal,
    EditMetaDataModal,
    IfFeature,
    InvalidContentModal,
    MetaData
} from "@app/components";
import { PleaseWaitModal } from "@apicurio/common-ui-components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { GroupMetaData } from "@models/groupMetaData.model.ts";
import { ArtifactsTabContent } from "@app/pages/group/components/tabs/ArtifactsTabContent.tsx";
import { ApiError } from "@models/apiError.model.ts";
import { SearchedArtifact } from "@models/searchedArtifact.model.ts";
import { CreateArtifact } from "@models/createArtifact.model.ts";


export type GroupPageProps = {
    // No properties
}

/**
 * The group page.
 */
export const GroupPage: FunctionComponent<GroupPageProps> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [activeTabKey, setActiveTabKey] = useState("overview");
    const [group, setGroup] = useState<GroupMetaData>();
    const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
    const [isDeleteArtifactModalOpen, setIsDeleteArtifactModalOpen] = useState(false);
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [isChangeOwnerModalOpen, setIsChangeOwnerModalOpen] = useState(false);
    const [isPleaseWaitModalOpen, setIsPleaseWaitModalOpen] = useState(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");
    const [isCreateArtifactModalOpen, setCreateArtifactModalOpen] = useState<boolean>(false);
    const [invalidContentError, setInvalidContentError] = useState<ApiError>();
    const [isInvalidContentModalOpen, setInvalidContentModalOpen] = useState<boolean>(false);
    const [artifactToDelete, setArtifactToDelete] = useState<SearchedArtifact>();
    const [artifactDeleteSuccessCallback, setArtifactDeleteSuccessCallback] = useState<() => void>();

    const appNavigation: AppNavigation = useAppNavigation();
    const logger: LoggerService = useLoggerService();
    const groups: GroupsService = useGroupsService();
    const { groupId }= useParams();

    const createLoaders = (): Promise<any>[] => {
        logger.info("Loading data for group: ", groupId);
        return [
            groups.getGroupMetaData(groupId as string)
                .then(setGroup)
                .catch(error => {
                    setPageError(toPageError(error, "Error loading page data."));
                }),
        ];
    };

    const handleTabClick = (_event: any, tabIndex: any): void => {
        setActiveTabKey(tabIndex);
    };

    const onDeleteGroup = (): void => {
        setIsDeleteModalOpen(true);
    };

    const onDeleteModalClose = (): void => {
        setIsDeleteModalOpen(false);
    };

    const onCreateArtifact = (): void => {
        setCreateArtifactModalOpen(true);
    };

    const onCreateArtifactModalClose = (): void => {
        setCreateArtifactModalOpen(false);
    };

    const doDeleteGroup = (): void => {
        onDeleteModalClose();
        pleaseWait(true, "Deleting group, please wait.");
        groups.deleteGroup(groupId as string).then( () => {
            pleaseWait(false);
            appNavigation.navigateTo("/explore");
        });
    };

    const doDeleteArtifact = (): void => {
        setIsDeleteArtifactModalOpen(false);
        pleaseWait(true, "Deleting artifact, please wait.");
        groups.deleteArtifact(groupId as string, artifactToDelete?.artifactId as string).then( () => {
            pleaseWait(false);
            if (artifactDeleteSuccessCallback) {
                artifactDeleteSuccessCallback();
            }
        });
    };

    const doCreateArtifact = (_groupId: string|null, data: CreateArtifact): void => {
        // Note: the create artifact modal passes the groupId, but we don't care about that because
        // this is the group page, so we know we want to create the artifact within this group!
        onCreateArtifactModalClose();
        pleaseWait(true, "Creating artifact, please wait.");
        groups.createArtifact(group?.groupId as string, data).then(response => {
            const groupId: string = response.artifact.groupId || "default";
            const artifactLocation: string = `/explore/${ encodeURIComponent(groupId) }/${ encodeURIComponent(response.artifact.artifactId) }`;
            logger.info("[ExplorePage] Artifact successfully created.  Redirecting to details page: ", artifactLocation);
            appNavigation.navigateTo(artifactLocation);
        }).catch( error => {
            pleaseWait(false);
            if (error && (error.error_code === 400 || error.error_code === 409)) {
                handleInvalidContentError(error);
            } else {
                setPageError(toPageError(error, "Error creating artifact."));
            }
        });
    };

    const closeInvalidContentModal = (): void => {
        setInvalidContentModalOpen(false);
    };

    const handleInvalidContentError = (error: any): void => {
        logger.info("[ExplorePage] Invalid content error:", error);
        setInvalidContentError(error);
        setInvalidContentModalOpen(true);
    };

    const onEditModalClose = (): void => {
        setIsEditModalOpen(false);
    };

    const onChangeOwnerModalClose = (): void => {
        setIsChangeOwnerModalOpen(false);
    };

    const doEditMetaData = (metaData: MetaData): void => {
        groups.updateGroupMetaData(groupId as string, metaData).then( () => {
            setGroup({
                ...(group as GroupMetaData),
                ...metaData
            });
        }).catch( error => {
            setPageError(toPageError(error, "Error editing group metadata."));
        });
        onEditModalClose();
    };

    const doChangeOwner = (newOwner: string): void => {
        groups.updateGroupOwner(groupId as string, newOwner).then( () => {
            setGroup({
                ...(group as GroupMetaData),
                owner: newOwner
            });
        }).catch( error => {
            setPageError(toPageError(error, "Error changing group ownership."));
        });
        onChangeOwnerModalClose();
    };

    const onViewArtifact = (artifact: SearchedArtifact): void => {
        const groupId: string = encodeURIComponent(group?.groupId || "default");
        const artifactId: string = encodeURIComponent(artifact.artifactId);
        appNavigation.navigateTo(`/explore/${groupId}/${artifactId}`);
    };

    const onDeleteArtifact = (artifact: SearchedArtifact, deleteSuccessCallback?: () => void): void => {
        setArtifactToDelete(artifact);
        setIsDeleteArtifactModalOpen(true);
        setArtifactDeleteSuccessCallback(() => deleteSuccessCallback);
    };

    const pleaseWait = (isOpen: boolean, message: string = ""): void => {
        setIsPleaseWaitModalOpen(isOpen);
        setPleaseWaitMessage(message);
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, [groupId]);

    const tabs: any[] = [
        <Tab data-testid="info-tab" eventKey="overview" title="Overview" key="overview" tabContentId="tab-info">
            <GroupInfoTabContent group={group as GroupMetaData} onEditMetaData={() => setIsEditModalOpen(true)} onChangeOwner={() => {}} />
        </Tab>,
        <Tab data-testid="artifacts-tab" eventKey="artifacts" title="Artifacts" key="artifacts" tabContentId="tab-artifacts">
            <ArtifactsTabContent
                group={group as GroupMetaData}
                onCreateArtifact={onCreateArtifact}
                onViewArtifact={onViewArtifact}
                onDeleteArtifact={onDeleteArtifact}
            />
        </Tab>,
    ];

    const breadcrumbs = (
        <Breadcrumb>
            <BreadcrumbItem><Link to={appNavigation.createLink("/explore")} data-testid="breadcrumb-lnk-explore">Explore</Link></BreadcrumbItem>
            <BreadcrumbItem isActive={true}>{ groupId as string }</BreadcrumbItem>
        </Breadcrumb>
    );

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <IfFeature feature="breadcrumbs" is={true}>
                    <PageSection className="ps_header-breadcrumbs" variant={PageSectionVariants.light} children={breadcrumbs} />
                </IfFeature>
                <PageSection className="ps_artifact-version-header" variant={PageSectionVariants.light}>
                    <GroupPageHeader title={groupId as string}
                        onDeleteGroup={onDeleteGroup}
                        groupId={groupId as string} />
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
            <EditMetaDataModal
                entityType="group"
                description={group?.description || ""}
                labels={group?.labels || {}}
                isOpen={isEditModalOpen}
                onClose={onEditModalClose}
                onEditMetaData={doEditMetaData} />
            <ConfirmDeleteModal isOpen={isDeleteModalOpen}
                title="Delete Group"
                message="Do you want to delete this group and all artifacts contained within? This action cannot be undone."
                onDelete={doDeleteGroup}
                onClose={onDeleteModalClose} />
            <ConfirmDeleteModal isOpen={isDeleteArtifactModalOpen}
                title="Delete Artifact"
                message="Do you want to delete the artifact and all its versions? This action cannot be undone."
                onDelete={doDeleteArtifact}
                onClose={() => {setIsDeleteArtifactModalOpen(false);}} />
            <ChangeOwnerModal
                isOpen={isChangeOwnerModalOpen}
                onClose={onChangeOwnerModalClose}
                currentOwner={group?.owner || ""}
                onChangeOwner={doChangeOwner}
            />
            <CreateArtifactModal
                isOpen={isCreateArtifactModalOpen}
                onClose={onCreateArtifactModalClose}
                onCreate={doCreateArtifact}
                groupId={group?.groupId} />
            <InvalidContentModal
                error={invalidContentError}
                isOpen={isInvalidContentModalOpen}
                onClose={closeInvalidContentModal} />
            <PleaseWaitModal message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen} />
        </PageErrorHandler>
    );

};
