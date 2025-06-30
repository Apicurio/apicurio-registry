import { FunctionComponent, useEffect, useState } from "react";
import "./BranchPage.css";
import { Breadcrumb, BreadcrumbItem, PageSection, PageSectionVariants, Tab, Tabs } from "@patternfly/react-core";
import { Link, useLocation, useParams } from "react-router-dom";
import {
    BranchVersionsTabContent,
    PageDataLoader,
    PageError,
    PageErrorHandler,
    PageProperties,
    toPageError
} from "@app/pages";
import { ConfirmDeleteModal, EditMetaDataModal, IfFeature, MetaData } from "@app/components";
import { PleaseWaitModal } from "@apicurio/common-ui-components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { ArtifactMetaData, BranchMetaData, SearchedVersion } from "@sdk/lib/generated-client/models";
import { BranchInfoTabContent, BranchPageHeader } from "@app/pages/branch/components";


/**
 * The artifact branch page.
 */
export const BranchPage: FunctionComponent<PageProperties> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [artifact, setArtifact] = useState<ArtifactMetaData>();
    const [branch, setBranch] = useState<BranchMetaData>();
    const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [isPleaseWaitModalOpen, setIsPleaseWaitModalOpen] = useState(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");

    const appNavigation: AppNavigation = useAppNavigation();
    const logger: LoggerService = useLoggerService();
    const groups: GroupsService = useGroupsService();
    const { groupId, artifactId, branchId } = useParams();
    const location = useLocation();

    let activeTabKey: string = "overview";
    if (location.pathname.indexOf("/content") !== -1) {
        activeTabKey = "content";
    } else if (location.pathname.indexOf("/versions") !== -1) {
        activeTabKey = "versions";
    }

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
            groups.getArtifactBranchMetaData(gid, artifactId as string, branchId as string)
                .then(setBranch)
                .catch(error => {
                    setPageError(toPageError(error, "Error loading page data."));
                }),
        ];
    };

    const handleTabClick = (_event: any, tabIndex: any): void => {
        const gid: string = encodeURIComponent(groupId as string);
        const aid: string = encodeURIComponent(artifactId as string);
        const bid: string = encodeURIComponent(branchId as string);
        if (tabIndex === "overview") {
            appNavigation.navigateTo(`/explore/${gid}/${aid}/branches/${bid}`);
        } else {
            appNavigation.navigateTo(`/explore/${gid}/${aid}/branches/${bid}/${tabIndex}`);
        }
    };

    const onDeleteBranch = (): void => {
        setIsDeleteModalOpen(true);
    };

    const onViewVersion = (version: SearchedVersion): void => {
        const groupId: string = encodeURIComponent(artifact?.groupId || "default");
        const artifactId: string = encodeURIComponent(artifact?.artifactId || "");
        const ver: string = encodeURIComponent(version.version!);
        appNavigation.navigateTo(`/explore/${groupId}/${artifactId}/versions/${ver}`);
    };

    const branchDescription = (): string => {
        return branch?.description || "";
    };

    const onDeleteModalClose = (): void => {
        setIsDeleteModalOpen(false);
    };

    const doDeleteBranch = (): void => {
        onDeleteModalClose();
        pleaseWait(true, "Deleting branch, please wait...");
        groups.deleteArtifactBranch(groupId as string, artifactId as string, branchId as string).then( () => {
            pleaseWait(false);
            const gid: string = encodeURIComponent(groupId || "default");
            const aid: string = encodeURIComponent(artifactId as string);
            appNavigation.navigateTo(`/explore/${gid}/${aid}/branches`);
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
        groups.updateArtifactBranchMetaData(groupId as string, artifactId as string, branchId as string, metaData).then( () => {
            if (artifact) {
                setBranch({
                    ...branch,
                    ...metaData
                } as BranchMetaData);
            }
        }).catch( error => {
            setPageError(toPageError(error, "Error editing branch metadata."));
        });
        onEditModalClose();
    };

    const pleaseWait = (isOpen: boolean, message: string = ""): void => {
        setIsPleaseWaitModalOpen(isOpen);
        setPleaseWaitMessage(message);
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, [groupId, artifactId, branchId]);

    const tabs: any[] = [
        <Tab data-testid="info-tab" eventKey="overview" title="Overview" key="overview" tabContentId="tab-info">
            <BranchInfoTabContent
                artifact={artifact as ArtifactMetaData}
                branch={branch as BranchMetaData}
                onEditMetaData={openEditMetaDataModal}
            />
        </Tab>,
        <Tab data-testid="versions-tab" eventKey="versions" title="Versions" key="versions">
            <BranchVersionsTabContent
                artifact={artifact as ArtifactMetaData}
                branch={branch as BranchMetaData}
                onViewVersion={onViewVersion}
            />
        </Tab>,
    ];

    const gid: string = groupId || "default";
    const hasGroup: boolean = gid != "default";
    let breadcrumbs = (
        <Breadcrumb>
            <BreadcrumbItem><Link to={appNavigation.createLink("/explore")} data-testid="breadcrumb-lnk-explore">Explore</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ encodeURIComponent(gid) }/artifacts`)}
                data-testid="breadcrumb-lnk-group">{ gid }</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ encodeURIComponent(gid) }/${ encodeURIComponent(artifactId||"") }/branches`)}
                data-testid="breadcrumb-lnk-artifact">{ artifactId }</Link></BreadcrumbItem>
            <BreadcrumbItem isActive={true}>{ branchId as string }</BreadcrumbItem>
        </Breadcrumb>
    );
    if (!hasGroup) {
        breadcrumbs = (
            <Breadcrumb>
                <BreadcrumbItem><Link to={appNavigation.createLink("/explore")} data-testid="breadcrumb-lnk-explore">Explore</Link></BreadcrumbItem>
                <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ encodeURIComponent(gid) }/${ encodeURIComponent(artifactId||"") }/branches`)}
                    data-testid="breadcrumb-lnk-artifact">{ artifactId }</Link></BreadcrumbItem>
                <BreadcrumbItem isActive={true}>{ branchId as string }</BreadcrumbItem>
            </Breadcrumb>
        );
    }

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <IfFeature feature="breadcrumbs" is={true}>
                    <PageSection className="ps_header-breadcrumbs" variant={PageSectionVariants.light} children={breadcrumbs} />
                </IfFeature>
                <PageSection className="ps_artifact-branch-header" variant={PageSectionVariants.light}>
                    <BranchPageHeader
                        artifact={artifact}
                        onDelete={onDeleteBranch}
                        branch={branch as BranchMetaData}
                        groupId={gid}
                        artifactId={artifactId as string} />
                </PageSection>
                <PageSection variant={PageSectionVariants.light} isFilled={true} padding={{ default: "noPadding" }} className="branch-details-main">
                    <Tabs className="branch-page-tabs"
                        id="branch-page-tabs"
                        unmountOnExit={true}
                        isFilled={false}
                        activeKey={activeTabKey}
                        children={tabs}
                        onSelect={handleTabClick}
                    />
                </PageSection>
            </PageDataLoader>
            <ConfirmDeleteModal
                title="Delete Branch"
                message="Do you want to delete this branch?  This action cannot be undone."
                isOpen={isDeleteModalOpen}
                onDelete={doDeleteBranch}
                onClose={onDeleteModalClose} />
            <EditMetaDataModal
                entityType="branch"
                description={branchDescription()}
                isOpen={isEditModalOpen}
                onClose={onEditModalClose}
                onEditMetaData={doEditMetaData}
            />
            <PleaseWaitModal message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen} />
        </PageErrorHandler>
    );

};
