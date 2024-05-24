import { FunctionComponent, useState } from "react";
import "./RoleToolbar.css";
import { If, ObjectSelect } from "@apicurio/common-ui-components";
import { RoleMapping, RoleTypes } from "@models/roleMapping.model.ts";
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
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { Paging } from "@models/paging.model.ts";


type FilterType = {
    type: string;
    label: string;
};
const FILTER_TYPES: FilterType[] = [
    {
        type: "account",
        label: "Account"
    },
    {
        type: "role",
        label: "Role"
    }
];
const DEFAULT_FILTER_TYPE: FilterType = FILTER_TYPES[0];

type RoleType = {
    type: string;
    label: string;
};
const ROLE_TYPES: RoleType[] = [
    {
        type: "",
        label: "All Roles"
    },
    {
        type: RoleTypes.READ_ONLY,
        label: "Viewer"
    },
    {
        type: RoleTypes.DEVELOPER,
        label: "Manager"
    },
    {
        type: RoleTypes.ADMIN,
        label: "Administrator"
    }
];
const DEFAULT_ROLE_TYPE: FilterType = ROLE_TYPES[0];


export type RoleToolbarCriteria = {
    filterType: string;
    filterValue: string;
    ascending: boolean;
    paging: Paging;
};


/**
 * Properties
 */
export type RoleToolbarProps = {
    roles: RoleMapping[];
    onCriteriaChange: (criteria: RoleToolbarCriteria) => void;
    onGrantAccess: () => void;
};

/**
 * Models the role list toolbar.
 */
export const RoleToolbar: FunctionComponent<RoleToolbarProps> = (props: RoleToolbarProps) => {
    const [filterType, setFilterType] = useState(DEFAULT_FILTER_TYPE);
    const [filterValue, setFilterValue] = useState("");
    const [filterAscending, setFilterAscending] = useState(true);
    const [roleType, setRoleType] = useState(DEFAULT_ROLE_TYPE);
    const [paging, setPaging] = useState<Paging>({
        page: 1,
        pageSize: 10
    });

    const logger: LoggerService = useLoggerService();

    const getCriteriaValue = (): string => {
        return filterType.type === "account" ? filterValue : roleType.type;
    };

    const fireChangeEvent = (ascending: boolean, filterType: string, filterValue: string, paging: Paging): void => {
        const criteria: RoleToolbarCriteria = {
            ascending,
            filterType,
            filterValue,
            paging
        };
        props.onCriteriaChange(criteria);
    };

    const onFilterSubmit = (event: any|undefined): void => {
        fireChangeEvent(filterAscending, filterType.type, filterValue, paging);
        if (event) {
            event.preventDefault();
        }
    };

    const onFilterTypeChange = (newType: FilterType): void => {
        setFilterType(newType);
        if  (newType.type === "account") {
            setFilterValue("");
            fireChangeEvent(filterAscending, newType.type, "", paging);
        } else if (newType.type === "role") {
            setRoleType(DEFAULT_ROLE_TYPE);
            fireChangeEvent(filterAscending, newType.type, DEFAULT_ROLE_TYPE.type, paging);
        }
    };

    const onRoleTypeChange = (roleType: RoleType): void => {
        setRoleType(roleType);
        fireChangeEvent(filterAscending, filterType.type, roleType.type, paging);
    };

    const onToggleAscending = (): void => {
        logger.debug("[ArtifactsPageToolbar] Toggle the ascending flag.");
        const newAscending: boolean = !filterAscending;
        setFilterAscending(newAscending);
        fireChangeEvent(newAscending, filterType.type, getCriteriaValue(), paging);
    };

    const onSetPage = (_event: any, newPage: number, perPage?: number): void => {
        const newPaging: Paging = {
            page: newPage,
            pageSize: perPage ? perPage : paging.pageSize
        };
        setPaging(newPaging);
        fireChangeEvent(filterAscending, filterType.type, getCriteriaValue(), newPaging);
    };

    const onPerPageSelect = (_event: any, newPerPage: number): void => {
        const newPaging: Paging = {
            page: paging.page,
            pageSize: newPerPage
        };
        setPaging(newPaging);
        fireChangeEvent(filterAscending, filterType.type, getCriteriaValue(), newPaging);
    };

    return (
        <Toolbar id="roles-toolbar-1" className="roles-toolbar">
            <ToolbarContent>
                <ToolbarItem className="filter-item">
                    <Form onSubmit={onFilterSubmit}>
                        <InputGroup>
                            <ObjectSelect
                                value={filterType}
                                items={FILTER_TYPES}
                                toggleClassname="filter-types-toggle"
                                onSelect={onFilterTypeChange}
                                itemToString={(item) => item.label} />
                            <If condition={filterType.type === "account"}>
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
                            </If>
                            <If condition={filterType.type === "role"}>
                                <ObjectSelect value={roleType} items={ROLE_TYPES} toggleClassname="role-types-toggle"
                                    onSelect={onRoleTypeChange} itemToString={(item) => item.label} />
                            </If>
                        </InputGroup>
                    </Form>
                </ToolbarItem>
                <ToolbarItem className="sort-icon-item">
                    <Button variant="plain" aria-label="edit" data-testid="toolbar-btn-sort" onClick={onToggleAscending}>
                        {
                            filterAscending ? <SortAlphaDownIcon/> : <SortAlphaDownAltIcon/>
                        }
                    </Button>
                </ToolbarItem>
                <ToolbarItem className="grant-access-item">
                    <Button variant="primary" data-testid="btn-grant-access" onClick={props.onGrantAccess}>Grant access</Button>
                </ToolbarItem>
                <ToolbarItem className="artifact-paging-item" align={{ default: "alignRight" }}>
                    <Pagination
                        variant="top"
                        dropDirection="down"
                        itemCount={props.roles?.length || 0}
                        perPage={paging.pageSize}
                        page={paging.page}
                        onSetPage={onSetPage}
                        onPerPageSelect={onPerPageSelect}
                        widgetId="artifact-list-pagination"
                        className="artifact-list-pagination"
                    />
                </ToolbarItem>
            </ToolbarContent>
        </Toolbar>
    );
};
