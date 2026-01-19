import { FunctionComponent, useEffect, useState } from "react";
import "./SearchPage.css";
import { PageSection, PageSectionVariants, TextContent } from "@patternfly/react-core";
import {
    SearchArtifactList,
    PageDataLoader,
    PageError,
    PageErrorHandler,
    PageProperties,
    SEARCH_PAGE_IDX,
    SearchPageEmptyState,
    SearchPageToolbar,
    toPageError, SearchGroupList, SearchVersionList
} from "@app/pages";
import { RootPageHeader } from "@app/components";
import { If, ListWithToolbar } from "@apicurio/common-ui-components";
import { SearchType } from "@app/pages/search/SearchType.ts";
import { Paging } from "@models/Paging.ts";
import { FilterBy, SearchFilter, useSearchService } from "@services/useSearchService.ts";
import {
    ArtifactSearchResults,
    ArtifactSortBy,
    ArtifactSortByObject,
    GroupSearchResults,
    GroupSortBy,
    GroupSortByObject, SearchedGroup, SearchedVersion,
    VersionSearchResults,
    VersionSortBy,
    VersionSortByObject
} from "@sdk/lib/generated-client/models";
import { SortOrder } from "@models/SortOrder.ts";
import { useAppNavigation } from "@services/useAppNavigation.ts";

const EMPTY_RESULTS: ArtifactSearchResults = {
    artifacts: [],
    count: 0
};

const DEFAULT_PAGING: Paging = {
    page: 1,
    pageSize: 10
};

/**
 * The Search page.
 */
export const SearchPage: FunctionComponent<PageProperties> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [searchType, setSearchType] = useState(SearchType.ARTIFACT);
    const [filters, setFilters] = useState<SearchFilter[]>([]);
    const [sortBy, setSortBy] = useState<GroupSortBy | ArtifactSortBy | VersionSortBy>(ArtifactSortByObject.ArtifactId);
    const [sortOrder, setSortOrder] = useState<SortOrder>(SortOrder.asc);
    const [isSearching, setSearching] = useState<boolean>(false);
    const [paging, setPaging] = useState<Paging>(DEFAULT_PAGING);
    const [results, setResults] = useState<ArtifactSearchResults | GroupSearchResults | VersionSearchResults>(EMPTY_RESULTS);

    const searchSvc = useSearchService();
    const appNav = useAppNavigation();

    const createLoaders = (): Promise<any> => {
        return search(searchType, filters, sortBy, sortOrder, paging);
    };

    const onResultsLoaded = (results: ArtifactSearchResults | GroupSearchResults): void => {
        setSearching(false);
        setResults(results);
    };


    const onFilterChange = (filters: SearchFilter[]): void => {
        setFilters(filters);
        search(searchType, filters, sortBy, sortOrder, paging);
    };

    const onFilterByLabel = (key: string, value: string | undefined): void => {
        let filterValue: string = key;
        if (value) {
            filterValue += ":" + value;
        }
        const dsf: SearchFilter = {
            by: FilterBy.labels,
            value: filterValue
        };

        let updated: boolean = false;
        const newFilters: SearchFilter[] = filters.map(filter => {
            if (filter.by === FilterBy.labels) {
                updated = true;
                return dsf;
            } else {
                return filter;
            }
        });
        if (!updated) {
            newFilters.push(dsf);
        }
        setFilters(newFilters);
        search(searchType, newFilters, sortBy, sortOrder, paging);
    };

    const isFiltered = (): boolean => {
        return filters.length > 0;
    };

    const search = async (searchType: SearchType, filters: SearchFilter[],
        sortBy: GroupSortBy | ArtifactSortBy | VersionSortBy, sortOrder: SortOrder, paging: Paging): Promise<any> =>
    {
        setSearching(true);

        if (searchType === SearchType.ARTIFACT) {
            return searchSvc.searchArtifacts(filters, sortBy as ArtifactSortBy, sortOrder, paging).then(results => {
                onResultsLoaded(results);
            }).catch(error => {
                setPageError(toPageError(error, "Error searching for artifacts."));
            });
        } else if (searchType === SearchType.GROUP) {
            return searchSvc.searchGroups(filters, sortBy as GroupSortBy, sortOrder, paging).then(results => {
                onResultsLoaded(results);
            }).catch(error => {
                setPageError(toPageError(error, "Error searching for groups."));
            });
        } else if (searchType === SearchType.VERSION) {
            return searchSvc.searchVersions(filters, sortBy as VersionSortBy, sortOrder, paging).then(results => {
                onResultsLoaded(results);
            }).catch(error => {
                setPageError(toPageError(error, "Error searching for versions."));
            });
        }
    };

    const onSortChange = (sortBy: VersionSortBy | GroupSortBy | ArtifactSortBy, sortOrder: SortOrder): void => {
        setSortBy(sortBy);
        setSortOrder(sortOrder);
        search(searchType, filters, sortBy, sortOrder, paging);
    };

    const onSearchTypeChange = (newSearchType: SearchType): void => {
        const newFilters: SearchFilter[] = [];
        const newPaging: Paging = DEFAULT_PAGING;
        let newSortBy: GroupSortBy | ArtifactSortBy | VersionSortBy;
        const newSortOrder: SortOrder = SortOrder.asc;

        switch (newSearchType) {
            case SearchType.GROUP:
                newSortBy = GroupSortByObject.GroupId;
                break;
            case SearchType.ARTIFACT:
                newSortBy = ArtifactSortByObject.ArtifactId;
                break;
            case SearchType.VERSION:
                newSortBy = VersionSortByObject.ArtifactId;
                break;
        }

        setPaging(newPaging);
        setFilters(newFilters);
        setSearchType(newSearchType);
        setSortBy(newSortBy);
        setSortOrder(newSortOrder);

        search(newSearchType, newFilters, newSortBy, newSortOrder, newPaging);
    };

    const onEditVersion = (version: SearchedVersion): void => {
        const gid: string = encodeURIComponent(version.groupId || "default");
        const aid: string = encodeURIComponent(version.artifactId!);
        const ver: string = encodeURIComponent(version.version!);
        const link: string = `/explore/${gid}/${aid}/versions/${ver}/edit`;
        appNav.navigateTo(link);
    };

    const onExploreGroup = (group: SearchedGroup): void => {
        const gid: string = encodeURIComponent(group.groupId || "default");
        const link: string = `/explore/${gid}`;
        appNav.navigateTo(link);
    };

    const onExploreArtifact = (artifact: SearchedVersion): void => {
        const gid: string = encodeURIComponent(artifact.groupId || "default");
        const aid: string = encodeURIComponent(artifact.artifactId!);
        const link: string = `/explore/${gid}/${aid}`;
        appNav.navigateTo(link);
    };

    const onExploreVersion = (version: SearchedVersion): void => {
        const gid: string = encodeURIComponent(version.groupId || "default");
        const aid: string = encodeURIComponent(version.artifactId!);
        const ver: string = encodeURIComponent(version.version!);
        const link: string = `/explore/${gid}/${aid}/versions/${ver}`;
        appNav.navigateTo(link);
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, []);

    const toolbar = (
        <SearchPageToolbar
            searchType={searchType}
            results={results}
            filters={filters}
            paging={paging}
            sortBy={sortBy}
            sortOrder={sortOrder}
            onPageChange={(paging: Paging) => {
                setPaging(paging);
                search(searchType, filters, sortBy, sortOrder, paging);
            }}
            onSearchTypeChange={onSearchTypeChange}
            onRefresh={() => {
                search(searchType, filters, sortBy, sortOrder, paging);
            }}
            onSortChange={onSortChange}
            onFilterChange={onFilterChange} />
    );

    const emptyState = (
        <SearchPageEmptyState
            searchType={searchType}
            isFiltered={isFiltered()}/>
    );

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection className="ps_search-header" variant={PageSectionVariants.light} padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={SEARCH_PAGE_IDX} />
                </PageSection>
                <PageSection className="ps_search-description" variant={PageSectionVariants.light}>
                    <TextContent>
                        Search content in the registry by searching for artifacts, versions, or groups.
                    </TextContent>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    <ListWithToolbar toolbar={toolbar}
                        emptyState={emptyState}
                        filteredEmptyState={emptyState}
                        alwaysShowToolbar={true}
                        isLoading={isSearching}
                        isError={false}
                        isFiltered={isFiltered()}
                        isEmpty={results.count === 0}>
                        <If condition={searchType === SearchType.ARTIFACT}>
                            <SearchArtifactList
                                artifacts={(results as ArtifactSearchResults).artifacts!}
                                onExplore={onExploreArtifact}
                                onFilterByLabel={onFilterByLabel}
                            />
                        </If>
                        <If condition={searchType === SearchType.GROUP}>
                            <SearchGroupList
                                groups={(results as GroupSearchResults).groups!}
                                onExplore={onExploreGroup}
                                onFilterByLabel={onFilterByLabel}
                            />
                        </If>
                        <If condition={searchType === SearchType.VERSION}>
                            <SearchVersionList
                                versions={(results as VersionSearchResults).versions!}
                                onEdit={onEditVersion}
                                onExplore={onExploreVersion}
                                onFilterByLabel={onFilterByLabel}
                            />
                        </If>
                    </ListWithToolbar>
                </PageSection>
            </PageDataLoader>
        </PageErrorHandler>
    );

};
