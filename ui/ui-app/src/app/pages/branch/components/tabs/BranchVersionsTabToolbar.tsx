import { FunctionComponent } from "react";
import "./BranchVersionsTabToolbar.css";
import { Pagination, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core";
import { Paging } from "@models/paging.model.ts";
import { VersionSearchResults } from "@sdk/lib/generated-client/models";


/**
 * Properties
 */
export type BranchVersionsToolbarProps = {
    results: VersionSearchResults;
    paging: Paging;
    onPageChange: (paging: Paging) => void;
};


/**
 * Models the toolbar for the Versions tab on the Branch page.
 */
export const BranchVersionsTabToolbar: FunctionComponent<BranchVersionsToolbarProps> = (props: BranchVersionsToolbarProps) => {

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
        <Toolbar id="branches-toolbar-1" className="branches-toolbar">
            <ToolbarContent>
                <ToolbarItem className="paging-item" align={{ default: "alignRight" }}>
                    <Pagination
                        variant="top"
                        dropDirection="down"
                        itemCount={ props.results.count }
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
