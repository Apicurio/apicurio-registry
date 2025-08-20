import { FunctionComponent } from "react";
import "./SearchPageEmptyState.css";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    EmptyStateIcon,
    EmptyStateVariant,
    Title
} from "@patternfly/react-core";
import { PlusCircleIcon } from "@patternfly/react-icons";
import { IfAuth, IfFeature } from "@app/components";
import { If } from "@apicurio/common-ui-components";
import { SearchType } from "@app/pages/search/SearchType.ts";

/**
 * Properties
 */
export type SearchPageEmptyStateProps = {
    searchType: SearchType;
    isFiltered: boolean;
    onCreateArtifact: () => void;
    onCreateGroup: () => void;
    onImport: () => void;
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
        <EmptyState variant={EmptyStateVariant.full}>
            <EmptyStateIcon icon={PlusCircleIcon}/>
            <Title headingLevel="h5" size="lg">No { entityPlural } found</Title>
            <If condition={() => props.isFiltered}>
                <EmptyStateBody>
                    No {entityPlural} match your filter settings.  Change your filter or perhaps Create a new {entitySingular}.
                </EmptyStateBody>
            </If>
            <If condition={() => !props.isFiltered}>
                <EmptyStateBody>
                    There are currently no {entityPlural} in the registry.  Create one or more {entityPlural} to view them here.
                </EmptyStateBody>
            </If>
            <EmptyStateFooter>
                <EmptyStateActions>
                    <IfAuth isDeveloper={true}>
                        <If condition={props.searchType === SearchType.ARTIFACT}>
                            <IfFeature feature="readOnly" isNot={true}>
                                <Button className="empty-btn-create" variant="primary"
                                    data-testid="empty-btn-create" onClick={props.onCreateArtifact}>Create artifact</Button>
                            </IfFeature>
                        </If>
                        <If condition={props.searchType === SearchType.GROUP}>
                            <IfFeature feature="readOnly" isNot={true}>
                                <Button className="empty-btn-create" variant="primary"
                                    data-testid="empty-btn-create" onClick={props.onCreateGroup}>Create group</Button>
                            </IfFeature>
                        </If>
                    </IfAuth>
                    <IfAuth isAdmin={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button className="empty-btn-import" variant="secondary"
                                data-testid="empty-btn-import" onClick={props.onImport}>Import from zip</Button>
                        </IfFeature>
                    </IfAuth>
                </EmptyStateActions>
            </EmptyStateFooter>
        </EmptyState>
    );

};
