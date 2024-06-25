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
    Toolbar,
    ToolbarContent,
    ToolbarItem
} from "@patternfly/react-core";
import { SearchIcon } from "@patternfly/react-icons";
import { OnPerPageSelect, OnSetPage } from "@patternfly/react-core/dist/js/components/Pagination/Pagination";
import { ObjectSelect } from "@apicurio/common-ui-components";
import { Paging } from "@models/paging.model.ts";
import { ArtifactReference, ReferenceType, ReferenceTypeObject } from "@sdk/lib/generated-client/models";

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
                            <Button variant={ButtonVariant.control}
                                onClick={onFilterSubmit}
                                data-testid="toolbar-btn-filter-search"
                                aria-label="search button for search input">
                                <SearchIcon/>
                            </Button>
                        </InputGroup>
                    </Form>
                </ToolbarItem>
                <ToolbarItem className="type-toggle-item" style={{ paddingTop: "6px" }}>
                    <Switch
                        id="simple-switch"
                        label="View artifacts that reference this artifact"
                        labelOff="View artifacts that reference this artifact"
                        isChecked={ props.referenceType === ReferenceTypeObject.INBOUND }
                        onChange={ props.onToggleReferenceType }
                    />
                </ToolbarItem>
                <ToolbarItem className="paging-item" align={{ default: "alignRight" }}>
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
