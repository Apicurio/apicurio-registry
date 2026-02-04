import { FunctionComponent, useEffect, useState } from "react";
import "./ExplorePageToolbar.css";
import {
    Button,
    Form,
    InputGroup,
    Pagination,
    SearchInput,
    Toolbar,
    ToolbarContent,
    ToolbarItem
} from "@patternfly/react-core";
import { SortAlphaDownAltIcon, SortAlphaDownIcon } from "@patternfly/react-icons";
import { OnPerPageSelect, OnSetPage } from "@patternfly/react-core/dist/js/components/Pagination/Pagination";
import { ObjectDropdown } from "@apicurio/common-ui-components";
import { Paging } from "@models/Paging.ts";
import { FilterBy } from "@services/useSearchService.ts";
import { GroupSearchResults } from "@apicurio/apicurio-registry-sdk/dist/generated-client/models";
import { IfAuth, IfFeature } from "@app/components";
import { useConfigService } from "@services/useConfigService.ts";

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
    const [groupFilterType] = useState(GROUP_FILTER_TYPES[0]);
    const [filterValue, setFilterValue] = useState("");
    const [filterAscending, setFilterAscending] = useState(true);
    const [kebabActions, setKebabActions] = useState<ActionType[]>([]);

    const config = useConfigService();

    const onFilterChange = (event: any | undefined, value: string): void => {
        setFilterValue(value);
    };

    const onFilterClear = (event: any | undefined): void => {
        setFilterValue("");
        const filterTypeValue: FilterBy = groupFilterType.value;
        fireChangeEvent(filterAscending, filterTypeValue, "");
        if (event) {
            event.preventDefault();
        }
    };

    const onFilterSubmit = (event: any | undefined): void => {
        const filterTypeValue: FilterBy = groupFilterType.value;
        fireChangeEvent(filterAscending, filterTypeValue, filterValue);
        if (event) {
            event.preventDefault();
        }
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
                <ToolbarItem className="filter-item">
                    <Form onSubmit={onFilterSubmit}>
                        <InputGroup>
                            <SearchInput
                                placeholder="Filter groups..."
                                value={filterValue}
                                onChange={onFilterChange}
                                onSearch={onFilterSubmit}
                                onClear={onFilterClear}
                            />
                        </InputGroup>
                    </Form>
                </ToolbarItem>
                <ToolbarItem className="sort-icon-item">
                    <Button icon={
                        filterAscending ? <SortAlphaDownIcon/> : <SortAlphaDownAltIcon/>
                    } variant="plain" aria-label="edit" data-testid="artifact-filter-sort"
                    onClick={onToggleAscending} />
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
                <ToolbarItem className="artifact-paging-item" align={{ default: "alignEnd" }}>
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
