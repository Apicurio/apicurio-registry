import { FunctionComponent } from "react";
import "./SearchPageEmptyState.css";
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
import { SearchType } from "@app/pages/search/SearchType.ts";

/**
 * Properties
 */
export type SearchPageEmptyStateProps = {
    searchType: SearchType;
    isFiltered: boolean;
    onAction?: () => void;
    onCreateArtifact?: () => void;
};

/**
 * Models the empty state for the Search page (when there are no results).
 */
export const SearchPageEmptyState: FunctionComponent<SearchPageEmptyStateProps> = (props: SearchPageEmptyStateProps) => {
    let entitySingular: string;
    let entityPlural: string;

    switch (props.searchType) {
        case SearchType.ARTIFACT:
            entitySingular = "artifact";
            entityPlural = "artifacts";
            break;
        case SearchType.GROUP:
            entitySingular = "group";
            entityPlural = "groups";
            break;
        case SearchType.VERSION:
            entitySingular = "version";
            entityPlural = "versions";
            break;
    }

    return (
        <EmptyState titleText={`No ${entityPlural} found`} icon={PlusCircleIcon} variant={EmptyStateVariant.full}>
            <If condition={() => props.isFiltered}>
                <EmptyStateBody>
                    No {entityPlural} match your filter settings.  Change your filter or perhaps create a new {entitySingular}.
                </EmptyStateBody>
            </If>
            <If condition={() => !props.isFiltered}>
                <EmptyStateBody>
                    There are currently no {entityPlural} in the registry.  Create one or more {entityPlural} to view them here.
                </EmptyStateBody>
            </If>
            <EmptyStateFooter>
                <If condition={() => !props.isFiltered && props.searchType === SearchType.GROUP}>
                    <EmptyStateActions>
                        <Button variant="primary" data-testid="empty-btn-create-group" onClick={props.onAction}>
                            Create group
                        </Button>
                    </EmptyStateActions>
                </If>
                <If condition={() => !!props.onCreateArtifact}>
                    <EmptyStateActions>
                        <Button className="empty-btn-create" variant="primary"
                            icon={<PlusCircleIcon />}
                            data-testid="empty-btn-create" onClick={props.onCreateArtifact}>Create artifact</Button>
                    </EmptyStateActions>
                </If>
            </EmptyStateFooter>
        </EmptyState>
    );
};
