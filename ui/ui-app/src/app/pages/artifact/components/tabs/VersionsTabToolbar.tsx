import { FunctionComponent } from "react";
import "./VersionsTabToolbar.css";
import { Button, Pagination, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core";
import { Paging } from "@models/paging.model.ts";
import { IfAuth, IfFeature } from "@app/components";
import { VersionSearchResults } from "@models/versionSearchResults.model.ts";


/**
 * Properties
 */
export type VersionsToolbarProps = {
    results: VersionSearchResults;
    paging: Paging;
    onPageChange: (paging: Paging) => void;
    onCreateVersion: () => void;
};


/**
 * Models the toolbar for the Versions tab on the Artifact page.
 */
export const VersionsTabToolbar: FunctionComponent<VersionsToolbarProps> = (props: VersionsToolbarProps) => {

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
        <Toolbar id="references-toolbar-1" className="references-toolbar">
            <ToolbarContent>
                <ToolbarItem className="create-version-item">
                    <IfAuth isDeveloper={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button className="btn-header-create-version" data-testid="btn-toolbar-create-version"
                                variant="primary" onClick={props.onCreateVersion}>Create version</Button>
                        </IfFeature>
                    </IfAuth>
                </ToolbarItem>
                <ToolbarItem className="paging-item" align={{ default: "alignRight" }}>
                    <Pagination
                        variant="top"
                        dropDirection="down"
                        itemCount={ props.results.count }
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
