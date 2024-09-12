import { FunctionComponent, useEffect, useState } from "react";
import "./VersionPage.css";
import { Breadcrumb, BreadcrumbItem, PageSection, PageSectionVariants, Tab, Tabs } from "@patternfly/react-core";
import { Link, useLocation, useParams } from "react-router-dom";
import {
    ContentTabContent,
    DocumentationTabContent,
    VersionInfoTabContent,
    PageDataLoader,
    PageError,
    PageErrorHandler,
    toPageError,
    VersionPageHeader
} from "@app/pages";
import { ReferencesTabContent } from "@app/pages/version/components/tabs/ReferencesTabContent.tsx";
import { ConfirmDeleteModal, EditMetaDataModal, IfFeature, MetaData } from "@app/components";
import { ContentTypes } from "@models/contentTypes.model.ts";
import { PleaseWaitModal } from "@apicurio/common-ui-components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { DownloadService, useDownloadService } from "@services/useDownloadService.ts";
import { ArtifactTypes } from "@services/useArtifactTypesService.ts";
import { ArtifactMetaData, Labels, VersionMetaData } from "@sdk/lib/generated-client/models";


export type ArtifactVersionPageProps = {
    // No properties
}

/**
 * The artifact version page.
 */
export const VersionPage: FunctionComponent<ArtifactVersionPageProps> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [artifact, setArtifact] = useState<ArtifactMetaData>();
    const [artifactVersion, setArtifactVersion] = useState<VersionMetaData>();
    const [versionContent, setArtifactContent] = useState("");
    const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [isPleaseWaitModalOpen, setIsPleaseWaitModalOpen] = useState(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");

    const appNavigation: AppNavigation = useAppNavigation();
    const logger: LoggerService = useLoggerService();
    const groups: GroupsService = useGroupsService();
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
        groups.deleteArtifactVersion(groupId as string, artifactId as string, version as string).then( () => {
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

    const pleaseWait = (isOpen: boolean, message: string = ""): void => {
        setIsPleaseWaitModalOpen(isOpen);
        setPleaseWaitMessage(message);
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, [groupId, artifactId, version]);

    const tabs: any[] = [
        <Tab data-testid="info-tab" eventKey="overview" title="Overview" key="overview" tabContentId="tab-info">
            <VersionInfoTabContent
                artifact={artifact as ArtifactMetaData}
                version={artifactVersion as VersionMetaData}
                onEditMetaData={openEditMetaDataModal}
            />
        </Tab>,
        <Tab data-testid="documentation-tab" eventKey="documentation" title="Documentation" key="documentation" className="documentation-tab">
            <DocumentationTabContent versionContent={versionContent} artifactType={artifact?.artifactType as string} />
        </Tab>,
        <Tab data-testid="content-tab" eventKey="content" title="Content" key="content">
            <ContentTabContent versionContent={versionContent} artifactType={artifact?.artifactType as string} />
        </Tab>,
        <Tab data-testid="references-tab" eventKey="references" title="References" key="references">
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
            <BreadcrumbItem><Link to={appNavigation.createLink("/explore")} data-testid="breadcrumb-lnk-explore">Explore</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ encodeURIComponent(gid) }/artifacts`)}
                data-testid="breadcrumb-lnk-group">{ gid }</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ encodeURIComponent(gid) }/${ encodeURIComponent(artifactId||"") }/versions`)}
                data-testid="breadcrumb-lnk-artifact">{ artifactId }</Link></BreadcrumbItem>
            <BreadcrumbItem isActive={true}>{ version as string }</BreadcrumbItem>
        </Breadcrumb>
    );
    if (!hasGroup) {
        breadcrumbs = (
            <Breadcrumb>
                <BreadcrumbItem><Link to={appNavigation.createLink("/explore")} data-testid="breadcrumb-lnk-explore">Explore</Link></BreadcrumbItem>
                <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ encodeURIComponent(gid) }/${ encodeURIComponent(artifactId||"") }/versions`)}
                    data-testid="breadcrumb-lnk-artifact">{ artifactId }</Link></BreadcrumbItem>
                <BreadcrumbItem isActive={true}>{ version as string }</BreadcrumbItem>
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
                    <VersionPageHeader
                        onDelete={onDeleteVersion}
                        onDownload={doDownloadVersion}
                        version={version as string}
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
            <PleaseWaitModal message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen} />
        </PageErrorHandler>
    );

};
