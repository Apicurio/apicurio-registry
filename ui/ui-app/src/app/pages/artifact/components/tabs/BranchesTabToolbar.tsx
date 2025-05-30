import { FunctionComponent } from "react";
import "./BranchesTabToolbar.css";
import { Button, Pagination, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core";
import { Paging } from "@models/paging.model.ts";
import { IfAuth, IfFeature } from "@app/components";
import { ArtifactMetaData, BranchSearchResults } from "@sdk/lib/generated-client/models";


/**
 * Properties
 */
export type BranchesToolbarProps = {
    artifact: ArtifactMetaData;
    results: BranchSearchResults;
    paging: Paging;
    onPageChange: (paging: Paging) => void;
    onCreateBranch: () => void;
};


/**
 * Models the toolbar for the Branches tab on the Artifact page.
 */
export const BranchesTabToolbar: FunctionComponent<BranchesToolbarProps> = (props: BranchesToolbarProps) => {

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
                <ToolbarItem className="create-branch-item">
                    <IfAuth isDeveloper={true} owner={props.artifact.owner}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button className="btn-header-create-branch" data-testid="btn-toolbar-create-branch"
                                variant="primary" onClick={props.onCreateBranch}>Create branch</Button>
                        </IfFeature>
                    </IfAuth>
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
                        widgetId="reference-list-pagination"
                        className="reference-list-pagination"
                    />
                </ToolbarItem>
            </ToolbarContent>
        </Toolbar>
    );
};
