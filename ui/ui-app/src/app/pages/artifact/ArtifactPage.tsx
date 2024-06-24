import { FunctionComponent, useEffect, useState } from "react";
import "./ArtifactPage.css";
import { Breadcrumb, BreadcrumbItem, PageSection, PageSectionVariants, Tab, Tabs } from "@patternfly/react-core";
import { Link, useParams } from "react-router-dom";
import { ArtifactMetaData } from "@models/artifactMetaData.model.ts";
import { PageDataLoader, PageError, PageErrorHandler, toPageError } from "@app/pages";
import {
    ChangeOwnerModal,
    ConfirmDeleteModal,
    CreateVersionModal,
    EditMetaDataModal,
    IfFeature,
    InvalidContentModal, MetaData
} from "@app/components";
import { PleaseWaitModal } from "@apicurio/common-ui-components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import {
    ArtifactInfoTabContent,
    ArtifactPageHeader,
    VersionsTabContent
} from "@app/pages/artifact/components";
import { SearchedVersion } from "@models/searchedVersion.model.ts";
import { CreateVersion } from "@models/createVersion.model.ts";
import { ApiError } from "@models/apiError.model.ts";
import { Rule, RuleType } from "@sdk/lib/generated-client/models";


export type ArtifactPageProps = {
    // No properties
}

/**
 * The artifact version page.
 */
export const ArtifactPage: FunctionComponent<ArtifactPageProps> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [activeTabKey, setActiveTabKey] = useState("overview");
    const [artifact, setArtifact] = useState<ArtifactMetaData>();
    const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
    const [isDeleteVersionModalOpen, setIsDeleteVersionModalOpen] = useState(false);
    const [isCreateVersionModalOpen, setIsCreateVersionModalOpen] = useState(false);
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [isChangeOwnerModalOpen, setIsChangeOwnerModalOpen] = useState(false);
    const [isPleaseWaitModalOpen, setIsPleaseWaitModalOpen] = useState(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");
    const [rules, setRules] = useState<Rule[]>([]);
    const [invalidContentError, setInvalidContentError] = useState<ApiError>();
    const [isInvalidContentModalOpen, setIsInvalidContentModalOpen] = useState(false);
    const [versionToDelete, setVersionToDelete] = useState<SearchedVersion>();
    const [versionDeleteSuccessCallback, setVersionDeleteSuccessCallback] = useState<() => void>();

    const appNavigation: AppNavigation = useAppNavigation();
    const logger: LoggerService = useLoggerService();
    const groups: GroupsService = useGroupsService();
    const { groupId, artifactId }= useParams();

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
            groups.getArtifactRules(gid, artifactId as string)
                .then(setRules)
                .catch(error => {
                    setPageError(toPageError(error, "Error loading page data."));
                }),
        ];
    };

    const handleTabClick = (_event: any, tabIndex: any): void => {
        setActiveTabKey(tabIndex);
    };

    const onDeleteArtifact = (): void => {
        setIsDeleteModalOpen(true);
    };

    const doEnableRule = (ruleType: string): void => {
        logger.debug("[ArtifactPage] Enabling rule:", ruleType);
        let config: string = "FULL";
        if (ruleType === "COMPATIBILITY") {
            config = "BACKWARD";
        }
        groups.createArtifactRule(groupId as string, artifactId as string, ruleType, config).catch(error => {
            setPageError(toPageError(error, `Error enabling "${ ruleType }" artifact rule.`));
        });
        setRules([...rules, { config, ruleType: ruleType as RuleType }]);
    };

    const doDisableRule = (ruleType: string): void => {
        logger.debug("[ArtifactPage] Disabling rule:", ruleType);
        groups.deleteArtifactRule(groupId as string, artifactId as string, ruleType).catch(error => {
            setPageError(toPageError(error, `Error disabling "${ ruleType }" artifact rule.`));
        });
        setRules(rules.filter(r => r.ruleType !== ruleType));
    };

    const doConfigureRule = (ruleType: string, config: string): void => {
        logger.debug("[ArtifactPage] Configuring rule:", ruleType, config);
        groups.updateArtifactRule(groupId as string, artifactId as string, ruleType, config).catch(error => {
            setPageError(toPageError(error, `Error configuring "${ ruleType }" artifact rule.`));
        });
        setRules(rules.map(r => {
            if (r.ruleType === ruleType) {
                return { config, ruleType: r.ruleType };
            } else {
                return r;
            }
        }));
    };

    const onDeleteModalClose = (): void => {
        setIsDeleteModalOpen(false);
    };

    const onDeleteVersionModalClose = (): void => {
        setIsDeleteVersionModalOpen(false);
    };

    const doDeleteArtifact = (): void => {
        onDeleteModalClose();
        pleaseWait(true, "Deleting artifact, please wait...");
        groups.deleteArtifact(groupId as string, artifactId as string).then( () => {
            pleaseWait(false, "");
            appNavigation.navigateTo("/explore");
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

    const doEditMetaData = (metaData: MetaData): void => {
        groups.updateArtifactMetaData(groupId as string, artifactId as string, metaData).then( () => {
            if (artifact) {
                setArtifact({
                    ...artifact,
                    ...metaData
                });
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

    const onViewVersion = (version: SearchedVersion): void => {
        const groupId: string = encodeURIComponent(artifact?.groupId || "default");
        const artifactId: string = encodeURIComponent(artifact?.artifactId || "");
        const ver: string = encodeURIComponent(version.version);
        appNavigation.navigateTo(`/explore/${groupId}/${artifactId}/${ver}`);
    };

    const onDeleteVersion = (version: SearchedVersion, successCallback?: () => void): void => {
        setVersionToDelete(version);
        setIsDeleteVersionModalOpen(true);
        setVersionDeleteSuccessCallback(() => successCallback);
    };

    const doDeleteVersion = (): void => {
        setIsDeleteVersionModalOpen(false);
        pleaseWait(true, "Deleting version, please wait...");
        groups.deleteArtifactVersion(groupId as string, artifactId as string, versionToDelete?.version as string).then( () => {
            pleaseWait(false);
            if (versionDeleteSuccessCallback) {
                versionDeleteSuccessCallback();
            }
        }).catch(error => {
            setPageError(toPageError(error, "Error deleting a version."));
        });
    };

    const handleInvalidContentError = (error: any): void => {
        logger.info("INVALID CONTENT ERROR", error);
        setInvalidContentError(error);
        setIsInvalidContentModalOpen(true);
    };

    const doCreateArtifactVersion = (data: CreateVersion): void => {
        setIsCreateVersionModalOpen(false);
        pleaseWait(true, "Creating a new version, please wait...");

        groups.createArtifactVersion(groupId as string, artifactId as string, data).then(versionMetaData => {
            const groupId: string = encodeURIComponent(versionMetaData.groupId ? versionMetaData.groupId : "default");
            const artifactId: string = encodeURIComponent(versionMetaData.artifactId);
            const version: string = encodeURIComponent(versionMetaData.version);
            const artifactVersionLocation: string = `/explore/${groupId}/${artifactId}/${version}`;
            logger.info("[ArtifactPage] Artifact version successfully created.  Redirecting to details: ", artifactVersionLocation);
            pleaseWait(false);
            appNavigation.navigateTo(artifactVersionLocation);
        }).catch( error => {
            pleaseWait(false);
            if (error && (error.error_code === 400 || error.error_code === 409)) {
                handleInvalidContentError(error);
            } else {
                setPageError(toPageError(error, "Error creating artifact version."));
            }
        });
    };

    const pleaseWait = (isOpen: boolean, message: string = ""): void => {
        setIsPleaseWaitModalOpen(isOpen);
        setPleaseWaitMessage(message);
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, [groupId, artifactId]);

    const tabs: any[] = [
        <Tab data-testid="info-tab" eventKey="overview" title="Overview" key="overview" tabContentId="tab-info">
            <ArtifactInfoTabContent
                artifact={artifact as ArtifactMetaData}
                rules={rules}
                onEnableRule={doEnableRule}
                onDisableRule={doDisableRule}
                onConfigureRule={doConfigureRule}
                onEditMetaData={openEditMetaDataModal}
                onChangeOwner={openChangeOwnerModal}
            />
        </Tab>,
        <Tab data-testid="versions-tab" eventKey="versions" title="Versions" key="versions" tabContentId="tab-versions">
            <VersionsTabContent
                artifact={artifact as ArtifactMetaData}
                onCreateVersion={() => {setIsCreateVersionModalOpen(true);}}
                onViewVersion={onViewVersion}
                onDeleteVersion={onDeleteVersion}
            />
        </Tab>,
    ];

    const gid: string = groupId || "default";
    const hasGroup: boolean = gid != "default";
    let breadcrumbs = (
        <Breadcrumb>
            <BreadcrumbItem><Link to={appNavigation.createLink("/explore")} data-testid="breadcrumb-lnk-explore">Explore</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ encodeURIComponent(gid) }`)}
                data-testid="breadcrumb-lnk-group">{ gid }</Link></BreadcrumbItem>
            <BreadcrumbItem isActive={true}>{ artifactId as string }</BreadcrumbItem>
        </Breadcrumb>
    );
    if (!hasGroup) {
        breadcrumbs = (
            <Breadcrumb>
                <BreadcrumbItem><Link to="/explore" data-testid="breadcrumb-lnk-explore">Explore</Link></BreadcrumbItem>
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
                    <ArtifactPageHeader
                        artifact={artifact as ArtifactMetaData}
                        onDeleteArtifact={onDeleteArtifact} />
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
                title="Delete Artifact"
                message="Do you want to delete this artifact and all of its versions?  This action cannot be undone."
                isOpen={isDeleteModalOpen}
                onDelete={doDeleteArtifact}
                onClose={onDeleteModalClose} />
            <ConfirmDeleteModal
                title="Delete Version"
                message="Do you want to delete the artifact version?  This action cannot be undone."
                isOpen={isDeleteVersionModalOpen}
                onDelete={doDeleteVersion}
                onClose={onDeleteVersionModalClose} />
            <EditMetaDataModal
                entityType="artifact"
                name={artifact?.name || ""}
                description={artifact?.description || ""}
                labels={artifact?.labels || {}}
                isOpen={isEditModalOpen}
                onClose={onEditModalClose}
                onEditMetaData={doEditMetaData}
            />
            <CreateVersionModal
                artifactType={artifact?.artifactType || ""}
                isOpen={isCreateVersionModalOpen}
                onClose={() => setIsCreateVersionModalOpen(false)}
                onCreate={doCreateArtifactVersion}
            />
            <ChangeOwnerModal isOpen={isChangeOwnerModalOpen}
                onClose={onChangeOwnerModalClose}
                currentOwner={artifact?.owner || ""}
                onChangeOwner={doChangeOwner}
            />
            <PleaseWaitModal message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen} />
            <InvalidContentModal error={invalidContentError}
                isOpen={isInvalidContentModalOpen}
                onClose={() => {setIsInvalidContentModalOpen(false);}} />
        </PageErrorHandler>
    );

};
