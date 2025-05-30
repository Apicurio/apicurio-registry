import { FunctionComponent } from "react";
import "./VersionsTabToolbar.css";
import { Button, Pagination, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core";
import { Paging } from "@models/paging.model.ts";
import { IfAuth, IfFeature } from "@app/components";
import { ArtifactMetaData, VersionSearchResults } from "@sdk/lib/generated-client/models";


/**
 * Properties
 */
export type VersionsToolbarProps = {
    artifact: ArtifactMetaData;
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
        <Toolbar id="versions-toolbar-1" className="versions-toolbar">
            <ToolbarContent>
                <ToolbarItem className="create-version-item">
                    <IfAuth isDeveloper={true} owner={props.artifact.owner}>
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
