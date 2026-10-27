import { FunctionComponent } from "react";
import "./DraftsPageEmptyState.css";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    EmptyStateVariant
} from "@patternfly/react-core";
import { PlusCircleIcon } from "@patternfly/react-icons";
import { If } from "@apitomy/common-ui-components";
import { IfFeature } from "@app/components";

/**
 * Properties
 */
export type DraftsPageEmptyStateProps = {
    isFiltered: boolean;
    onCreateDraft: () => void;
};


/**
 * Models the empty state for the Drafts page (when there are no results).
 */
export const DraftsPageEmptyState: FunctionComponent<DraftsPageEmptyStateProps> = (props: DraftsPageEmptyStateProps) => {
    return (
        <EmptyState titleText="No drafts found" icon={PlusCircleIcon} variant={EmptyStateVariant.full}>
            <If condition={() => props.isFiltered}>
                <EmptyStateBody>
                    No drafts match your filter settings.  Change your filter or perhaps create a new draft.
                </EmptyStateBody>
            </If>
            <If condition={() => !props.isFiltered}>
                <EmptyStateBody>
                    There are currently no drafts in the registry.  Create one or more draft to view them here.
                </EmptyStateBody>
            </If>
            <IfFeature feature="readOnly" isNot={true}>
                <EmptyStateFooter>
                    <EmptyStateActions>
                        <Button className="empty-btn-create" variant="primary"
                            data-testid="empty-btn-create" onClick={props.onCreateDraft}>Create draft</Button>
                    </EmptyStateActions>
                </EmptyStateFooter>
            </IfFeature>
        </EmptyState>
    );

};
