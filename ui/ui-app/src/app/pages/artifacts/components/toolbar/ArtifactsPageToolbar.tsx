import  { FunctionComponent, useEffect, useState } from "react";
import "./ArtifactsPageToolbar.css";
import {
    Button,
    ButtonVariant,
    Form,
    InputGroup,
    Pagination,
    TextInput,
    Toolbar,
    ToolbarContent,
    ToolbarItem
} from "@patternfly/react-core";
import { SearchIcon, SortAlphaDownAltIcon, SortAlphaDownIcon } from "@patternfly/react-icons";
import { IfAuth, IfFeature } from "@app/components";
import { Services } from "@services/services.ts";
import { ArtifactsSearchResults, Paging } from "@services/groups";
import { OnPerPageSelect, OnSetPage } from "@patternfly/react-core/dist/js/components/Pagination/Pagination";
import { ObjectDropdown, ObjectSelect } from "@apicurio/common-ui-components";

export type ArtifactsPageToolbarFilterCriteria = {
    filterSelection: string;
    filterValue: string;
    ascending: boolean;
};

export type FilterByGroupFunction = (groupId: string) => void;

export type ArtifactsPageToolbarProps = {
    artifacts: ArtifactsSearchResults;
    onCriteriaChange: (criteria: ArtifactsPageToolbarFilterCriteria) => void;
    criteria: ArtifactsPageToolbarFilterCriteria;
    paging: Paging;
    onPerPageSelect: OnPerPageSelect;
    onSetPage: OnSetPage;
    onUploadArtifact: () => void;
    onImportArtifacts: () => void;
    onExportArtifacts: () => void;
    filterByGroupHook: (hook: FilterByGroupFunction) => void;
};

type FilterType = {
    value: string;
    label: string;
    testId: string;
};
const FILTER_TYPES: FilterType[] = [
    { value: "name", label: "Name", testId: "artifact-filter-typename" },
    { value: "group", label: "Group", testId: "artifact-filter-typegroup" },
    { value: "description", label: "Description", testId: "artifact-filter-typedescription" },
    { value: "labels", label: "Labels", testId: "artifact-filter-typelabels" },
    { value: "globalId", label: "Global Id", testId: "artifact-filter-typeglobal-id" },
    { value: "contentId", label: "Content Id", testId: "artifact-filter-typecontent-id" },
];
const DEFAULT_FILTER_TYPE = FILTER_TYPES[0];


type ActionType = {
    label: string;
    callback: () => void;
};

/**
 * Models the toolbar for the Artifacts page.
 */
export const ArtifactsPageToolbar: FunctionComponent<ArtifactsPageToolbarProps> = (props: ArtifactsPageToolbarProps) => {
    const [filterType, setFilterType] = useState(DEFAULT_FILTER_TYPE);
    const [filterValue, setFilterValue] = useState("");
    const [filterAscending, setFilterAscending] = useState(true);
    const [kebabActions, setKebabActions] = useState<ActionType[]>([]);

    const totalArtifactsCount = (): number => {
        return props.artifacts ? props.artifacts.count : 0;
    };

    const onFilterSubmit = (event: any|undefined): void => {
        fireChangeEvent(filterAscending, filterType.value, filterValue);
        if (event) {
            event.preventDefault();
        }
    };

    const onFilterTypeChange = (newType: FilterType): void => {
        setFilterType(newType);
        fireChangeEvent(filterAscending, newType.value, filterValue);
    };

    const onToggleAscending = (): void => {
        Services.getLoggerService().debug("[ArtifactsPageToolbar] Toggle the ascending flag.");
        const newAscending: boolean = !filterAscending;
        setFilterAscending(newAscending);
        fireChangeEvent(newAscending, filterType.value, filterValue);
    };

    const fireChangeEvent = (ascending: boolean, filterSelection: string, filterValue: string): void => {
        const criteria: ArtifactsPageToolbarFilterCriteria = {
            ascending,
            filterSelection,
            filterValue
        };
        props.onCriteriaChange(criteria);
    };

    const filterByGroup = (groupId: string): void => {
        Services.getLoggerService().info("[ArtifactsPageToolbar] Filtering by group: ", groupId);
        if (groupId) {
            const newFilterType: FilterType = FILTER_TYPES[1]; // Filter by group
            const newFilterValue: string = groupId;
            setFilterType(newFilterType);
            setFilterValue(newFilterValue);
            fireChangeEvent(filterAscending, newFilterType.value, newFilterValue);
        }
    };

    useEffect(() => {
        if (props.filterByGroupHook) {
            Services.getLoggerService().info("[ArtifactsPageToolbar] Setting change criteria hook");
            props.filterByGroupHook(filterByGroup);
        }
    }, []);

    useEffect(() => {
        const adminActions: ActionType[] = [
            { label: "Upload multiple artifacts", callback: props.onImportArtifacts },
            { label: "Download all artifacts (.zip file)", callback: props.onExportArtifacts }
        ];
        setKebabActions(adminActions);
    }, [props.onExportArtifacts, props.onImportArtifacts]);

    return (
        <Toolbar id="artifacts-toolbar-1" className="artifacts-toolbar">
            <ToolbarContent>
                <ToolbarItem className="filter-item">
                    <Form onSubmit={onFilterSubmit}>
                        <InputGroup>
                            <ObjectSelect
                                value={filterType}
                                items={FILTER_TYPES}
                                testId="artifact-filter-type-select"
                                toggleClassname="filter-types-toggle"
                                onSelect={onFilterTypeChange}
                                itemToTestId={(item) => item.testId}
                                itemToString={(item) => item.label} />
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
                    <Button variant="plain" aria-label="edit" data-testid="artifact-filter-sort" onClick={onToggleAscending}>
                        {
                            filterAscending ? <SortAlphaDownIcon/> : <SortAlphaDownAltIcon/>
                        }
                    </Button>
                </ToolbarItem>
                <ToolbarItem className="upload-artifact-item">
                    <IfAuth isDeveloper={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button className="btn-header-upload-artifact" data-testid="btn-toolbar-upload-artifact"
                                variant="primary" onClick={props.onUploadArtifact}>Upload artifact</Button>
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
                        itemCount={totalArtifactsCount()}
                        perPage={props.paging.pageSize}
                        page={props.paging.page}
                        onSetPage={props.onSetPage}
                        onPerPageSelect={props.onPerPageSelect}
                        widgetId="artifact-list-pagination"
                        className="artifact-list-pagination"
                    />
                </ToolbarItem>
            </ToolbarContent>
        </Toolbar>
    );

};
