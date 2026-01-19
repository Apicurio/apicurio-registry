import { FunctionComponent } from "react";
import "./SearchPageEmptyState.css";
import {
    EmptyState,
    EmptyStateBody,
    EmptyStateFooter,
    EmptyStateIcon,
    EmptyStateVariant,
    Title
} from "@patternfly/react-core";
import { PlusCircleIcon } from "@patternfly/react-icons";
import { If } from "@apicurio/common-ui-components";
import { SearchType } from "@app/pages/search/SearchType.ts";

/**
 * Properties
 */
export type SearchPageEmptyStateProps = {
    searchType: SearchType;
    isFiltered: boolean;
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
                    No {entityPlural} match your filter settings.  Change your filter or perhaps create a new {entitySingular}.
                </EmptyStateBody>
            </If>
            <If condition={() => !props.isFiltered}>
                <EmptyStateBody>
                    There are currently no {entityPlural} in the registry.  Create one or more {entityPlural} to view them here.
                </EmptyStateBody>
            </If>
            <EmptyStateFooter>
            </EmptyStateFooter>
        </EmptyState>
    );

};
