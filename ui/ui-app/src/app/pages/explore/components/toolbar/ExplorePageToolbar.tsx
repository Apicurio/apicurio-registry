import {FunctionComponent, useEffect, useState} from "react";
import "./ExplorePageToolbar.css";
import {
    Button,
    ButtonVariant,
    Form,
    InputGroup, Pagination,
    TextInput,
    Toolbar,
    ToolbarContent,
    ToolbarItem
} from "@patternfly/react-core";
import { SearchIcon, SortAlphaDownAltIcon, SortAlphaDownIcon } from "@patternfly/react-icons";
import { OnPerPageSelect, OnSetPage } from "@patternfly/react-core/dist/js/components/Pagination/Pagination";
import {ObjectDropdown, ObjectSelect} from "@apicurio/common-ui-components";
import { Paging } from "@models/Paging.ts";
import { FilterBy } from "@services/useSearchService.ts";
import { GroupSearchResults } from "@apicurio/apicurio-registry-sdk/dist/generated-client/models";
import { IfAuth, IfFeature } from "@app/components";
import {useConfigService} from "@services/useConfigService.ts";

export type ExplorePageToolbarFilterCriteria = {
    filterBy: FilterBy;
    filterValue: string;
    ascending: boolean;
};

export type ExplorePageToolbarProps = {
    results: GroupSearchResults;
    onCriteriaChange: (criteria: ExplorePageToolbarFilterCriteria) => void;
    criteria: ExplorePageToolbarFilterCriteria;
    paging: Paging;
    onPerPageSelect: OnPerPageSelect;
    onSetPage: OnSetPage;
    onCreateGroup: () => void;
    onImport: () => void;
    onExport: () => void;
};

type FilterType = {
    value: FilterBy;
    label: string;
    testId: string;
};
const GROUP_FILTER_TYPES: FilterType[] = [
    { value: FilterBy.groupId, label: "Group", testId: "group-filter-typegroup" },
    { value: FilterBy.description, label: "Description", testId: "group-filter-typedescription" },
    { value: FilterBy.labels, label: "Labels", testId: "group-filter-typelabels" },
];

type ActionType = {
    label: string;
    callback: () => void;
};

/**
 * Models the toolbar for the Explore page.
 */
export const ExplorePageToolbar: FunctionComponent<ExplorePageToolbarProps> = (props: ExplorePageToolbarProps) => {
    const [groupFilterType, setGroupFilterType] = useState(GROUP_FILTER_TYPES[0]);
    const [filterValue, setFilterValue] = useState("");
    const [filterAscending, setFilterAscending] = useState(true);
    const [kebabActions, setKebabActions] = useState<ActionType[]>([]);

    const config = useConfigService();

    const onFilterSubmit = (event: any | undefined): void => {
        const filterTypeValue: FilterBy = groupFilterType.value;
        fireChangeEvent(filterAscending, filterTypeValue, filterValue);
        if (event) {
            event.preventDefault();
        }
    };

    const onGroupFilterTypeChange = (newType: FilterType): void => {
        setGroupFilterType(newType);
        fireChangeEvent(filterAscending, newType.value, filterValue);
    };

    const onToggleAscending = (): void => {
        const filterTypeValue: FilterBy = groupFilterType.value;
        const newAscending: boolean = !filterAscending;
        setFilterAscending(newAscending);
        fireChangeEvent(newAscending, filterTypeValue, filterValue);
    };

    const fireChangeEvent = (ascending: boolean, filterBy: FilterBy, filterValue: string): void => {
        const criteria: ExplorePageToolbarFilterCriteria = {
            ascending,
            filterBy,
            filterValue
        };
        props.onCriteriaChange(criteria);
    };

    const groupCount = (): number => {
        return props?.results.count || 0;
    };

    useEffect(() => {
        const adminActions: ActionType[] = config.featureReadOnly() ? [
            { label: "Export all (as .ZIP)", callback: props.onExport }
        ] : [
            { label: "Import from .ZIP", callback: props.onImport },
            { label: "Export all (as .ZIP)", callback: props.onExport }
        ];
        setKebabActions(adminActions);
    }, [props.onExport, props.onImport]);

    return (
        <Toolbar id="artifacts-toolbar-1" className="artifacts-toolbar">
            <ToolbarContent>
                <ToolbarItem variant="label">
                    Filter by
                </ToolbarItem>
                <ToolbarItem className="filter-item">
                    <Form onSubmit={onFilterSubmit}>
                        <InputGroup>
                            <ObjectSelect
                                value={groupFilterType}
                                items={GROUP_FILTER_TYPES}
                                testId="group-filter-type-select"
                                toggleClassname="group-filter-type-toggle"
                                onSelect={onGroupFilterTypeChange}
                                itemToTestId={(item) => item.testId}
                                itemToString={(item) => item.label}/>
                            <TextInput name="filterValue" id="filterValue" type="search"
                                value={filterValue}
                                onChange={(_evt, value) => setFilterValue(value)}
                                data-testid="artifact-filter-value"
                                aria-label="search input example"/>
                            <Button variant={ButtonVariant.control}
                                onClick={onFilterSubmit}
                                data-testid="artifact-filter-search"
                                aria-label="search button for search input">
                                <SearchIcon/>
                            </Button>
                        </InputGroup>
                    </Form>
                </ToolbarItem>
                <ToolbarItem className="sort-icon-item">
                    <Button variant="plain" aria-label="edit" data-testid="artifact-filter-sort"
                        onClick={onToggleAscending}>
                        {
                            filterAscending ? <SortAlphaDownIcon/> : <SortAlphaDownAltIcon/>
                        }
                    </Button>
                </ToolbarItem>
                <ToolbarItem className="create-artifact-item">
                    <IfAuth isDeveloper={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button className="btn-header-create-group" data-testid="btn-toolbar-create-group"
                                variant="primary" onClick={props.onCreateGroup}>Create group</Button>
                        </IfFeature>
                    </IfAuth>
                </ToolbarItem>
                <ToolbarItem className="admin-actions-item">
                    <IfAuth isAdmin={true}>
                        <ObjectDropdown
                            label="Admin actions"
                            items={kebabActions}
                            onSelect={(item) => item.callback()}
                            itemToString={(item) => item.label}
                            isKebab={true} />
                    </IfAuth>
                </ToolbarItem>
                <ToolbarItem className="artifact-paging-item" align={{ default: "alignRight" }}>
                    <Pagination
                        variant="top"
                        dropDirection="down"
                        itemCount={groupCount()}
                        perPage={props.paging.pageSize}
                        page={props.paging.page}
                        onSetPage={props.onSetPage}
                        onPerPageSelect={props.onPerPageSelect}
                        widgetId="group-list-pagination"
                        className="group-list-pagination"
                    />
                </ToolbarItem>
            </ToolbarContent>
        </Toolbar>
    );

};
