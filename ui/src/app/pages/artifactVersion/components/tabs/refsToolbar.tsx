import React from "react";
import "./refsToolbar.css";
import {
    Button,
    ButtonVariant,
    Dropdown,
    DropdownItem,
    DropdownToggle,
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
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import { OnPerPageSelect, OnSetPage } from "@patternfly/react-core/dist/js/components/Pagination/Pagination";
import { ArtifactReference } from "../../../../../models/artifactReference.model";
import { Paging, Services } from "../../../../../services";
import { ReferenceType } from "../../../../../models/referenceType";

export interface ReferencesToolbarFilterCriteria {
    filterSelection: string;
    filterValue: string;
}

/**
 * Properties
 */
export interface ReferencesToolbarProps extends PureComponentProps {
    referenceType: ReferenceType;
    references: ArtifactReference[];
    totalReferenceCount: number;
    onCriteriaChange: (criteria: ReferencesToolbarFilterCriteria) => void
    criteria: ReferencesToolbarFilterCriteria;
    paging: Paging;
    onPerPageSelect: OnPerPageSelect;
    onSetPage: OnSetPage;
    onToggleReferenceType: () => void;
}

/**
 * State
 */
export interface ReferencesToolbarState extends PureComponentState {
    filterIsExpanded: boolean;
    criteria: ReferencesToolbarFilterCriteria;
}

/**
 * Models the toolbar for the References tab on the Artifact Version page.
 */
export class ReferencesToolbar extends PureComponent<ReferencesToolbarProps, ReferencesToolbarState> {

    constructor(props: Readonly<ReferencesToolbarProps>) {
        super(props);
    }

    public componentDidUpdate(prevProps: ReferencesToolbarProps) {
        if (this.props.criteria && this.props.criteria != prevProps.criteria) {
            this.setSingleState("criteria", {
                filterSelection: this.props.criteria.filterSelection,
                filterValue: this.props.criteria.filterValue
            });
        }
    }

    public render(): React.ReactElement {
        return (
            <Toolbar id="references-toolbar-1" className="references-toolbar">
                <ToolbarContent>
                    <ToolbarItem className="filter-item">
                        <Form onSubmit={this.onFilterSubmit}>
                            <InputGroup>
                                <Dropdown
                                    onSelect={this.onFilterSelect}
                                    toggle={
                                        <DropdownToggle data-testid="toolbar-filter-toggle" onToggle={this.onFilterToggle}>{this.filterValueDisplay()}</DropdownToggle>
                                    }
                                    isOpen={this.state.filterIsExpanded}
                                    dropdownItems={[
                                        <DropdownItem key="name" id="name" data-testid="toolbar-filter-name" component="button">Name</DropdownItem>,
                                    ]}
                                />
                                <TextInput name="filterValue" id="filterValue" type="search"
                                    value={this.state.criteria.filterValue}
                                    onChange={this.onFilterValueChange}
                                    data-testid="toolbar-filter-value"
                                    aria-label="search input example"/>
                                <Button variant={ButtonVariant.control}
                                    onClick={this.onFilterSubmit}
                                    data-testid="toolbar-btn-filter-search"
                                    aria-label="search button for search input">
                                    <SearchIcon/>
                                </Button>
                            </InputGroup>
                        </Form>
                    </ToolbarItem>
                    <ToolbarItem className="type-toggle-item">
                        <Switch
                            id="simple-switch"
                            label="View artifacts that reference this artifact"
                            labelOff="View artifacts that reference this artifact"
                            isChecked={ this.props.referenceType === "INBOUND" }
                            onChange={ this.props.onToggleReferenceType }
                        />
                    </ToolbarItem>
                    <ToolbarItem className="paging-item" alignment={{ default: "alignRight" }}>
                        <Pagination
                            variant="bottom"
                            dropDirection="down"
                            itemCount={ this.props.totalReferenceCount }
                            perPage={ this.props.paging.pageSize }
                            page={ this.props.paging.page }
                            onSetPage={ this.props.onSetPage }
                            onPerPageSelect={ this.props.onPerPageSelect }
                            widgetId="reference-list-pagination"
                            className="reference-list-pagination"
                        />
                    </ToolbarItem>
                </ToolbarContent>
            </Toolbar>
        );
    }

    protected initializeState(): ReferencesToolbarState {
        return {
            filterIsExpanded: false,
            criteria: this.props.criteria
        };
    }

    private onFilterToggle = (isExpanded: boolean): void => {
        Services.getLoggerService().debug("[ReferencesToolbar] Toggling filter dropdown.");
        this.setSingleState("filterIsExpanded", isExpanded);
    };

    private onFilterSelect = (event: React.SyntheticEvent<HTMLDivElement>|undefined): void => {
        const value: string = event && event.currentTarget && event.currentTarget.id ? event.currentTarget.id : "";
        Services.getLoggerService().debug("[ReferencesToolbar] Setting filter type to: %s", value);
        this.setState({
            filterIsExpanded: false,
            criteria: {
                filterSelection: value,
                filterValue: this.state.criteria.filterValue
            }
        }, () => {
            this.fireOnChange();
        });
    };

    private onFilterValueChange = (value: any): void => {
        Services.getLoggerService().debug("[ReferencesToolbar] Setting filter value: %o", value);
        this.setSingleState("criteria", {
            filterSelection: this.state.criteria.filterSelection,
            filterValue: value
        });
    };

    private onFilterSubmit = (event: any|undefined): void => {
        this.fireOnChange();
        if (event) {
            event.preventDefault();
        }
    };

    private fireOnChange(): void {
        this.props.onCriteriaChange(this.state.criteria);
    }

    private filterValueDisplay(): string {
        switch (this.state.criteria.filterSelection) {
            case "name":
                return "Name";
            default:
                return "Name";
        }
    }
}
