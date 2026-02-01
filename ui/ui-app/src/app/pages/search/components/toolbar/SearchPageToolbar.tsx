import { FunctionComponent } from "react";
import "./SearchPageToolbar.css";
import {
    Button,
    capitalize,
    Pagination,
    Toolbar,
    ToolbarContent,
    ToolbarGroup,
    ToolbarItem
} from "@patternfly/react-core";
import { SyncAltIcon } from "@patternfly/react-icons";
import { SortOrderToggle } from "@app/components";
import {
    ChipFilterCriteria,
    ChipFilterInput,
    ChipFilterType,
    FilterChips,
    ObjectSelect
} from "@apicurio/common-ui-components";
import { SearchType } from "@app/pages/search/SearchType.ts";
import { plural } from "pluralize";
import { Paging } from "@models/Paging.ts";
import { FilterBy, SearchFilter } from "@services/useSearchService.ts";
import {
    ArtifactSearchResults,
    ArtifactSortBy,
    ArtifactSortByObject,
    GroupSearchResults,
    GroupSortBy,
    GroupSortByObject,
    VersionSearchResults,
    VersionSortBy,
    VersionSortByObject
} from "@sdk/lib/generated-client/models";
import { SortOrder } from "@models/SortOrder.ts";

export type SearchPageToolbarProps = {
    searchType: SearchType;
    results: ArtifactSearchResults | GroupSearchResults | VersionSearchResults;
    filters: SearchFilter[];
    paging: Paging;
    sortBy: VersionSortBy | GroupSortBy | ArtifactSortBy;
    sortOrder: SortOrder;

    onSearchTypeChange: (searchType: SearchType) => void;
    onFilterChange: (filters: SearchFilter[]) => void;
    onPageChange: (paging: Paging) => void;
    onSortChange: (sortBy: VersionSortBy | GroupSortBy | ArtifactSortBy, sortOrder: SortOrder) => void;
    onRefresh: () => void;
};

const ARTIFACT_FILTER_TYPES: ChipFilterType[] = [
    { value: FilterBy.name, label: "Name", testId: "artifact-filter-typename" },
    { value: FilterBy.groupId, label: "Group", testId: "artifact-filter-typegroup" },
    { value: FilterBy.description, label: "Description", testId: "artifact-filter-typedescription" },
    { value: FilterBy.labels, label: "Labels", testId: "artifact-filter-typelabels" },
    { value: FilterBy.globalId, label: "Global Id", testId: "artifact-filter-typeglobal-id" },
    { value: FilterBy.contentId, label: "Content Id", testId: "artifact-filter-typecontent-id" },
];
const GROUP_FILTER_TYPES: ChipFilterType[] = [
    { value: FilterBy.groupId, label: "Group", testId: "group-filter-typegroup" },
    { value: FilterBy.description, label: "Description", testId: "group-filter-typedescription" },
    { value: FilterBy.labels, label: "Labels", testId: "group-filter-typelabels" },
];
const VERSION_FILTER_TYPES: ChipFilterType[] = [
    { value: FilterBy.artifactId, label: "Artifact Id", testId: "artifact-id-filter-typegroup" },
    { value: FilterBy.artifactType, label: "Type", testId: "type-filter-typegroup" },
    { value: FilterBy.contentId, label: "Content Id", testId: "content-id-filter-typegroup" },
    { value: FilterBy.description, label: "Description", testId: "description-filter-typegroup" },
    { value: FilterBy.globalId, label: "Global  Id", testId: "global-id-filter-typegroup" },
    { value: FilterBy.groupId, label: "Group", testId: "group-filter-typegroup" },
    { value: FilterBy.labels, label: "Label", testId: "label-filter-typegroup" },
    { value: FilterBy.name, label: "Name", testId: "name-filter-typegroup" },
    { value: FilterBy.state, label: "State", testId: "state-filter-typegroup" },
    { value: FilterBy.version, label: "Version", testId: "version-filter-typegroup" },
];
const FILTER_TYPE_LOOKUP: any = {};
VERSION_FILTER_TYPES.forEach(filterType => {
    FILTER_TYPE_LOOKUP[filterType.value] = filterType;
});

/**
 * Models the toolbar for the Search page.
 */
export const SearchPageToolbar: FunctionComponent<SearchPageToolbarProps> = (props: SearchPageToolbarProps) => {
    const filterCriteria: ChipFilterCriteria[] = props.filters.map(c => {
        return {
            filterBy: FILTER_TYPE_LOOKUP[c.by],
            filterValue: c.value
        };
    });

    let filterTypes: ChipFilterType[];
    let sortItems: (VersionSortBy | GroupSortBy | ArtifactSortBy)[];
    switch (props.searchType) {
        case SearchType.ARTIFACT:
            filterTypes = ARTIFACT_FILTER_TYPES;
            sortItems = [
                ArtifactSortByObject.ArtifactId,
                ArtifactSortByObject.GroupId,
                ArtifactSortByObject.Name,
                ArtifactSortByObject.ModifiedOn
            ];
            break;
        case SearchType.GROUP:
            filterTypes = GROUP_FILTER_TYPES;
            sortItems = [
                GroupSortByObject.GroupId,
                GroupSortByObject.ModifiedOn,
                GroupSortByObject.CreatedOn
            ];
            break;
        case SearchType.VERSION:
            filterTypes = VERSION_FILTER_TYPES;
            sortItems = [
                VersionSortByObject.GroupId,
                VersionSortByObject.ArtifactId,
                VersionSortByObject.Version,
                VersionSortByObject.Name,
                VersionSortByObject.CreatedOn,
                VersionSortByObject.ModifiedOn,
                VersionSortByObject.GlobalId,
            ];
            break;
    }

    const totalCount = (): number => {
        return props.results.count!;
    };

    const onSetPage = (_event: any, newPage: number, perPage?: number): void => {
        const newPaging: Paging = {
            page: newPage,
            pageSize: perPage ? perPage : props.paging.pageSize
        };
        props.onPageChange(newPaging);
    };

    const onPerPageSelect = (_event: any, newPerPage: number): void => {
        const newPaging: Paging = {
            page: props.paging.page,
            pageSize: newPerPage
        };
        props.onPageChange(newPaging);
    };

    const fireFilterChange = (filters: SearchFilter[]): void => {
        props.onFilterChange(filters);
    };

    const onAddFilterCriteria = (criteria: ChipFilterCriteria): void => {
        if (criteria.filterValue === "") {
            fireFilterChange(props.filters);
        } else {
            const dsf: SearchFilter = {
                by: criteria.filterBy.value,
                value: criteria.filterValue
            };

            let updated: boolean = false;
            const newCriteria: SearchFilter[] = props.filters.map(filter => {
                if (filter.by === criteria.filterBy.value) {
                    updated = true;
                    return dsf;
                } else {
                    return filter;
                }
            });
            if (!updated) {
                newCriteria.push(dsf);
            }

            fireFilterChange(newCriteria);
        }
    };

    const onRemoveFilterCriteria = (criteria: ChipFilterCriteria): void => {
        const newFilters: SearchFilter[] = props.filters.filter(c => c.by !== criteria.filterBy.value);
        fireFilterChange(newFilters);
    };

    const onRemoveAllFilterCriteria = (): void => {
        fireFilterChange([]);
    };

    const sortByLabel = (sortBy: VersionSortBy | ArtifactSortBy | GroupSortBy): string => {
        switch (sortBy) {
            case GroupSortByObject.GroupId:
            case ArtifactSortByObject.GroupId:
            case VersionSortByObject.GroupId:
                return "Group Id";
            case ArtifactSortByObject.ArtifactId:
            case VersionSortByObject.ArtifactId:
                return "Artifact Id";
            case ArtifactSortByObject.Name:
            case VersionSortByObject.Name:
                return "Name";
            case GroupSortByObject.ModifiedOn:
            case ArtifactSortByObject.ModifiedOn:
            case VersionSortByObject.ModifiedOn:
                return "Modified On";
            case GroupSortByObject.CreatedOn:
            case ArtifactSortByObject.CreatedOn:
            case VersionSortByObject.CreatedOn:
                return "Created On";
            case ArtifactSortByObject.ArtifactType:
                return "Type";
            case VersionSortByObject.Version:
                return "Version";
            case VersionSortByObject.GlobalId:
                return "Global Id";
        }
        return "" + sortBy;
    };

    return (
        <div>
            <Toolbar id="artifacts-toolbar-1" className="artifacts-toolbar">
                <ToolbarContent>
                    <ToolbarGroup>
                        <ToolbarItem variant="label">
                            Search for
                        </ToolbarItem>
                        <ToolbarItem className="filter-item">
                            <ObjectSelect
                                value={props.searchType}
                                items={[SearchType.ARTIFACT, SearchType.GROUP, SearchType.VERSION]}
                                testId="search-type-select"
                                toggleClassname="search-type-toggle"
                                onSelect={props.onSearchTypeChange}
                                itemToTestId={(item) => `search-type-${plural(item.toString().toLowerCase())}`}
                                itemToString={(item) => capitalize(plural(item.toString().toLowerCase()))} />
                        </ToolbarItem>
                    </ToolbarGroup>
                    <ToolbarGroup>
                        <ToolbarItem variant="label">
                            filter by
                        </ToolbarItem>
                        <ToolbarItem className="filter-item">
                            <ChipFilterInput
                                filterTypes={filterTypes}
                                onAddCriteria={onAddFilterCriteria} />
                            <Button
                                variant="control" aria-label="Refresh"
                                className="btn-header-refresh" data-testid="btn-toolbar-refresh"
                                icon={<SyncAltIcon title="Refresh" />}
                                onClick={props.onRefresh}
                            />
                        </ToolbarItem>
                    </ToolbarGroup>
                    <ToolbarGroup>
                        <ToolbarItem variant="label" id="order-by-label">
                            Order by
                        </ToolbarItem>
                        <ToolbarItem className="ordering-item">
                            <ObjectSelect
                                value={props.sortBy}
                                items={sortItems}
                                onSelect={(newSortBy) => {
                                    props.onSortChange(newSortBy, props.sortOrder);
                                }}
                                itemToString={item => sortByLabel(item)}
                            />
                            <SortOrderToggle sortOrder={props.sortOrder} onChange={(newSortOrder => {
                                props.onSortChange(props.sortBy, newSortOrder);
                            })} />
                        </ToolbarItem>
                    </ToolbarGroup>
                </ToolbarContent>
            </Toolbar>
            <Toolbar id="search-toolbar-2" className="search-toolbar">
                <ToolbarContent>
                    <ToolbarItem className="filter-chips">
                        <FilterChips
                            criteria={filterCriteria}
                            onClearAllCriteria={onRemoveAllFilterCriteria}
                            onRemoveCriteria={onRemoveFilterCriteria} />
                    </ToolbarItem>
                    <ToolbarItem className="draft-paging-item" align={{ default: "alignEnd" }}>
                        <Pagination
                            variant="top"
                            dropDirection="down"
                            itemCount={totalCount()}
                            perPage={props.paging.pageSize}
                            page={props.paging.page}
                            onSetPage={onSetPage}
                            onPerPageSelect={onPerPageSelect}
                            widgetId="draft-list-pagination"
                            className="draft-list-pagination"
                        />
                    </ToolbarItem>
                </ToolbarContent>
            </Toolbar>
        </div>
    );

};
