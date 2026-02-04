import { FunctionComponent, useState } from "react";
import "./ReferencesToolbar.css";
import {
    Button,
    ButtonVariant,
    Form,
    InputGroup,
    Pagination,
    Switch,
    TextInput,
    ToggleGroup,
    ToggleGroupItem,
    Toolbar,
    ToolbarContent,
    ToolbarItem
} from "@patternfly/react-core";
import { ListIcon, SearchIcon, TopologyIcon } from "@patternfly/react-icons";
import { OnPerPageSelect, OnSetPage } from "@patternfly/react-core/dist/js/components/Pagination/Pagination";
import { ObjectSelect } from "@apicurio/common-ui-components";
import { Paging } from "@models/Paging.ts";
import { ArtifactReference, ReferenceType, ReferenceTypeObject } from "@sdk/lib/generated-client/models";

export type ViewMode = "list" | "graph";

export interface ReferencesToolbarFilterCriteria {
    filterSelection: string;
    filterValue: string;
}

/**
 * Properties
 */
export type ReferencesToolbarProps = {
    referenceType: ReferenceType;
    references: ArtifactReference[];
    totalReferenceCount: number;
    onCriteriaChange: (criteria: ReferencesToolbarFilterCriteria) => void
    criteria: ReferencesToolbarFilterCriteria;
    paging: Paging;
    onPerPageSelect: OnPerPageSelect;
    onSetPage: OnSetPage;
    onToggleReferenceType: () => void;
    viewMode: ViewMode;
    onViewModeChange: (mode: ViewMode) => void;
};

type FilterType = {
    value: string;
    label: string;
};
const FILTER_TYPES: FilterType[] = [
    { value: "name", label: "Name" }
];
const DEFAULT_FILTER_TYPE = FILTER_TYPES[0];

/**
 * Models the toolbar for the References tab on the Artifact Version page.
 */
export const ReferencesToolbar: FunctionComponent<ReferencesToolbarProps> = (props: ReferencesToolbarProps) => {
    const [filterType, setFilterType] = useState(DEFAULT_FILTER_TYPE);
    const [filterValue, setFilterValue] = useState("");

    const onFilterSubmit = (event: any|undefined): void => {
        fireOnChange();
        if (event) {
            event.preventDefault();
        }
    };

    const fireOnChange = (): void => {
        const criteria: ReferencesToolbarFilterCriteria = {
            filterSelection: filterType.value,
            filterValue
        };
        props.onCriteriaChange(criteria);
    };

    return (
        <Toolbar id="references-toolbar-1" className="references-toolbar">
            <ToolbarContent>
                <ToolbarItem className="filter-item">
                    <Form onSubmit={onFilterSubmit}>
                        <InputGroup>
                            <ObjectSelect
                                value={filterType}
                                items={FILTER_TYPES}
                                toggleClassname="filter-types-toggle"
                                onSelect={setFilterType}
                                itemToString={(item) => item.label} />
                            <TextInput name="filterValue" id="filterValue" type="search"
                                value={filterValue}
                                onChange={(_evt, value) => setFilterValue(value)}
                                data-testid="toolbar-filter-value"
                                aria-label="search input example"/>
                            <Button icon={<SearchIcon/>} variant={ButtonVariant.control}
                                onClick={onFilterSubmit}
                                data-testid="toolbar-btn-filter-search"
                                aria-label="search button for search input">
                                
                            </Button>
                        </InputGroup>
                    </Form>
                </ToolbarItem>
                <ToolbarItem className="type-toggle-item" style={{ paddingTop: "6px" }}>
                    <Switch
                        id="simple-switch"
                        label="View artifacts that reference this artifact"
                        
                        isChecked={ props.referenceType === ReferenceTypeObject.INBOUND }
                        onChange={ props.onToggleReferenceType }
                        data-testid="reference-type-toggle"
                    />
                </ToolbarItem>
                <ToolbarItem className="view-mode-toggle-item">
                    <ToggleGroup aria-label="View mode toggle" data-testid="view-mode-toggle">
                        <ToggleGroupItem
                            icon={<ListIcon />}
                            text="List"
                            aria-label="List view"
                            buttonId="view-mode-list"
                            data-testid="view-mode-list"
                            isSelected={props.viewMode === "list"}
                            onChange={() => props.onViewModeChange("list")}
                        />
                        <ToggleGroupItem
                            icon={<TopologyIcon />}
                            text="Graph"
                            aria-label="Graph view"
                            buttonId="view-mode-graph"
                            data-testid="view-mode-graph"
                            isSelected={props.viewMode === "graph"}
                            onChange={() => props.onViewModeChange("graph")}
                        />
                    </ToggleGroup>
                </ToolbarItem>
                <ToolbarItem className="paging-item" align={{ default: "alignEnd" }}>
                    <Pagination
                        variant="top"
                        dropDirection="down"
                        itemCount={ props.totalReferenceCount }
                        perPage={ props.paging.pageSize }
                        page={ props.paging.page }
                        onSetPage={ props.onSetPage }
                        onPerPageSelect={ props.onPerPageSelect }
                        widgetId="reference-list-pagination"
                        className="reference-list-pagination"
                    />
                </ToolbarItem>
            </ToolbarContent>
        </Toolbar>
    );
};
