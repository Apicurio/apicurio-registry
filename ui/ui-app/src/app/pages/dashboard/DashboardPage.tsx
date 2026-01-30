import { FunctionComponent, useEffect, useState } from "react";
import "./DashboardPage.css";
import {
    PageSection,
    Title,
    Grid,
    GridItem,
    Flex,
    FlexItem
} from "@patternfly/react-core";
import {
    CubesIcon,
    CodeBranchIcon,
    FolderOpenIcon
} from "@patternfly/react-icons";
import { DASHBOARD_PAGE_IDX, PageError, PageErrorHandler, PageProperties, toPageError } from "@app/pages";
import { CreateArtifactModal, RootPageHeader } from "@app/components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { SearchService, useSearchService } from "@services/useSearchService.ts";
import {
    ArtifactSortByObject,
    CreateArtifact,
    GroupSortByObject,
    SearchedArtifact,
    SortOrderObject,
    VersionSortByObject
} from "@sdk/lib/generated-client/models";
import { StatsCard, QuickActions, RecentArtifacts } from "./components";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";

interface RegistryStats {
    groupCount: number;
    artifactCount: number;
    versionCount: number;
}

/**
 * The registry dashboard page - main landing page showing stats and quick actions.
 */
export const DashboardPage: FunctionComponent<PageProperties> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [stats, setStats] = useState<RegistryStats>({
        groupCount: 0,
        artifactCount: 0,
        versionCount: 0
    });
    const [recentArtifacts, setRecentArtifacts] = useState<SearchedArtifact[]>([]);
    const [recentArtifactsError, setRecentArtifactsError] = useState<string | null>(null);
    const [isCreateArtifactModalOpen, setIsCreateArtifactModalOpen] = useState<boolean>(false);

    const appNavigation: AppNavigation = useAppNavigation();
    const search: SearchService = useSearchService();
    const groups: GroupsService = useGroupsService();

    const loadStats = async (): Promise<void> => {
        setIsLoading(true);
        try {
            const [groupsResult, artifactsResult, versionsResult] = await Promise.all([
                search.searchGroups([], GroupSortByObject.GroupId, SortOrderObject.Asc, { page: 1, pageSize: 1 }),
                search.searchArtifacts([], ArtifactSortByObject.ArtifactId, SortOrderObject.Asc, { page: 1, pageSize: 1 }),
                search.searchVersions([], VersionSortByObject.GlobalId, SortOrderObject.Asc, { page: 1, pageSize: 1 })
            ]);

            setStats({
                groupCount: groupsResult.count || 0,
                artifactCount: artifactsResult.count || 0,
                versionCount: versionsResult.count || 0
            });
        } catch (error) {
            console.error("Error loading stats:", error);
            setPageError(toPageError(error, "Error loading registry statistics."));
        } finally {
            setIsLoading(false);
        }
    };

    const loadRecentArtifacts = async (): Promise<void> => {
        try {
            const result = await search.searchArtifacts(
                [],
                ArtifactSortByObject.ModifiedOn,
                SortOrderObject.Desc,
                { page: 1, pageSize: 10 }
            );
            setRecentArtifacts(result.artifacts || []);
            setRecentArtifactsError(null);
        } catch (error) {
            console.error("Error loading recent artifacts:", error);
            setRecentArtifactsError("Failed to load recent artifacts.");
        }
    };

    useEffect(() => {
        loadStats();
        loadRecentArtifacts();
    }, []);

    const handleCreateArtifact = (): void => {
        setIsCreateArtifactModalOpen(true);
    };

    const handleSearch = (): void => {
        appNavigation.navigateTo("/search");
    };

    const handleExplore = (): void => {
        appNavigation.navigateTo("/explore");
    };

    const handleSettings = (): void => {
        appNavigation.navigateTo("/settings");
    };

    const handleStatsClick = (destination: string): void => {
        appNavigation.navigateTo(destination);
    };

    const doCreateArtifact = (groupId: string | undefined, data: CreateArtifact): void => {
        setIsCreateArtifactModalOpen(false);
        groups.createArtifact(groupId ?? null, data)
            .then((response) => {
                const gid = encodeURIComponent(response.artifact?.groupId || "default");
                const aid = encodeURIComponent(response.artifact?.artifactId || "");
                appNavigation.navigateTo(`/explore/${gid}/${aid}`);
            })
            .catch((error) => {
                setPageError(toPageError(error, "Error creating artifact."));
            });
    };

    return (
        <PageErrorHandler error={pageError}>
            <PageSection hasBodyWrapper={false} className="ps_dashboard-header"  padding={{ default: "noPadding" }}>
                <RootPageHeader tabKey={DASHBOARD_PAGE_IDX} />
            </PageSection>
            <PageSection hasBodyWrapper={false} className="ps_dashboard-description" >
                <Flex direction={{ default: "column" }}>
                    <FlexItem>
                        <Title headingLevel="h1">Registry Dashboard</Title>
                    </FlexItem>
                    <FlexItem>
                        <p className="dashboard-description">
                            Welcome to Apicurio Registry. Manage your schemas and API definitions in one central location.
                        </p>
                    </FlexItem>
                </Flex>
            </PageSection>
            <PageSection hasBodyWrapper={false} isFilled={true} className="ps_dashboard-content">
                <Grid hasGutter>
                    <GridItem span={12}>
                        <Flex spaceItems={{ default: "spaceItemsLg" }}>
                            <FlexItem>
                                <StatsCard
                                    title="Groups"
                                    value={stats.groupCount}
                                    icon={<FolderOpenIcon />}
                                    isLoading={isLoading}
                                    onClick={() => handleStatsClick("/explore?for=groups")}
                                />
                            </FlexItem>
                            <FlexItem>
                                <StatsCard
                                    title="Artifacts"
                                    value={stats.artifactCount}
                                    icon={<CubesIcon />}
                                    isLoading={isLoading}
                                    onClick={() => handleStatsClick("/search?for=artifacts")}
                                />
                            </FlexItem>
                            <FlexItem>
                                <StatsCard
                                    title="Versions"
                                    value={stats.versionCount}
                                    icon={<CodeBranchIcon />}
                                    isLoading={isLoading}
                                    onClick={() => handleStatsClick("/search?for=groups")}
                                />
                            </FlexItem>
                        </Flex>
                    </GridItem>
                    <GridItem span={8}>
                        <RecentArtifacts
                            artifacts={recentArtifacts}
                            isLoading={isLoading}
                            error={recentArtifactsError}
                        />
                    </GridItem>
                    <GridItem span={4}>
                        <QuickActions
                            onCreateArtifact={handleCreateArtifact}
                            onSearch={handleSearch}
                            onExplore={handleExplore}
                            onSettings={handleSettings}
                        />
                    </GridItem>
                </Grid>
            </PageSection>
            <CreateArtifactModal
                isOpen={isCreateArtifactModalOpen}
                onClose={() => setIsCreateArtifactModalOpen(false)}
                onCreate={doCreateArtifact}
            />
        </PageErrorHandler>
    );
};
