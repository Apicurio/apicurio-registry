import { FunctionComponent } from "react";
import "./ArtifactsTabToolbar.css";
import { Button, Pagination, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core";
import { Paging } from "@models/paging.model.ts";
import { IfAuth, IfFeature } from "@app/components";
import { ArtifactSearchResults } from "@sdk/lib/generated-client/models";


/**
 * Properties
 */
export type ArtifactsToolbarProps = {
    results: ArtifactSearchResults;
    paging: Paging;
    onPageChange: (paging: Paging) => void;
    onCreateArtifact: () => void;
};


/**
 * Models the toolbar for the Artifacts tab on the Group page.
 */
export const ArtifactsTabToolbar: FunctionComponent<ArtifactsToolbarProps> = (props: ArtifactsToolbarProps) => {

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
                <ToolbarItem className="create-artifact-item">
                    <IfAuth isDeveloper={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button className="btn-header-create-artifact" data-testid="btn-toolbar-create-artifact"
                                variant="primary" onClick={props.onCreateArtifact}>Create artifact</Button>
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
