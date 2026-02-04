import { FunctionComponent, useState } from "react";
import "./GroupArtifactsToolbar.css";
import { Pagination, SearchInput, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core";
import { Paging } from "@models/Paging.ts";
import { ArtifactSearchResults } from "@sdk/lib/generated-client/models";


/**
 * Properties
 */
export type GroupArtifactsToolbarProps = {
    results: ArtifactSearchResults;
    paging: Paging;
    onFilterChange: (filterBy: string) => void;
    onPageChange: (paging: Paging) => void;
};


/**
 * Models the toolbar for the Artifacts tab on the Group page.
 */
export const GroupArtifactsToolbar: FunctionComponent<GroupArtifactsToolbarProps> = (props: GroupArtifactsToolbarProps) => {
    const [filterValue, setFilterValue] = useState<string>("");

    const onFilterChange = (_event: any, value: string): void => {
        setFilterValue(value);
    };

    const onFilterSearch = (_event: any, value: string): void => {
        props.onFilterChange(value);
    };

    const onFilterClear = (): void => {
        setFilterValue("");
        props.onFilterChange("");
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

    return (
        <Toolbar id="group-artifacts-toolbar-1" className="group-artifacts-toolbar">
            <ToolbarContent>
                <ToolbarItem>
                    <SearchInput
                        placeholder="Filter artifacts..."
                        value={filterValue}
                        onChange={onFilterChange}
                        onSearch={onFilterSearch}
                        onClear={onFilterClear}
                    />
                </ToolbarItem>
                <ToolbarItem className="paging-item" align={{ default: "alignEnd" }}>
                    <Pagination
                        variant="top"
                        dropDirection="down"
                        itemCount={ props.results.count as number }
                        perPage={ props.paging.pageSize }
                        page={ props.paging.page }
                        onSetPage={ onSetPage }
                        onPerPageSelect={ onPerPageSelect }
                        widgetId="reference-list-pagination"
                        className="reference-list-pagination"
                    />
                </ToolbarItem>
            </ToolbarContent>
        </Toolbar>
    );
};
