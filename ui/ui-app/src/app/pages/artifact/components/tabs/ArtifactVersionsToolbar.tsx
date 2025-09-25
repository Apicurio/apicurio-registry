import { FunctionComponent, useState } from "react";
import "./ArtifactVersionsToolbar.css";
import { Pagination, SearchInput, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core";
import { Paging } from "@models/Paging.ts";
import { VersionSearchResults } from "@sdk/lib/generated-client/models";


/**
 * Properties
 */
export type ArtifactVersionsToolbarProps = {
    results: VersionSearchResults;
    paging: Paging;
    onFilterChange: (filterBy: string) => void;
    onPageChange: (paging: Paging) => void;
};


/**
 * Models the toolbar for the Versions tab on the Artifact page.
 */
export const ArtifactVersionsToolbar: FunctionComponent<ArtifactVersionsToolbarProps> = (props: ArtifactVersionsToolbarProps) => {
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
        <Toolbar id="artifact-versions-toolbar-1" className="artifact-versions-toolbar">
            <ToolbarContent>
                <ToolbarItem>
                    <SearchInput
                        placeholder="Filter versions..."
                        value={filterValue}
                        onChange={onFilterChange}
                        onSearch={onFilterSearch}
                        onClear={onFilterClear}
                    />
                </ToolbarItem>
                <ToolbarItem className="paging-item" align={{ default: "alignRight" }}>
                    <Pagination
                        variant="top"
                        dropDirection="down"
                        itemCount={ props.results.count as number }
                        perPage={ props.paging.pageSize }
                        page={ props.paging.page }
                        onSetPage={ onSetPage }
                        onPerPageSelect={ onPerPageSelect }
                        widgetId="version-list-pagination"
                        className="version-list-pagination"
                    />
                </ToolbarItem>
            </ToolbarContent>
        </Toolbar>
    );
};
