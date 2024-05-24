import { FunctionComponent, useEffect, useState } from "react";
import "./ArtifactsTabContent.css";
import "@app/styles/empty.css";
import { ListWithToolbar } from "@apicurio/common-ui-components";
import { GroupMetaData } from "@models/groupMetaData.model.ts";
import { ArtifactsTabToolbar } from "@app/pages/group/components/tabs/ArtifactsTabToolbar.tsx";
import { Paging } from "@models/paging.model.ts";
import { ArtifactSearchResults } from "@models/artifactSearchResults.model.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
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
import { ArtifactsTable } from "@app/pages/group/components/tabs/ArtifactsTable.tsx";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { ArtifactSortBy } from "@models/artifactSortBy.model.ts";
import { SortOrder } from "@models/sortOrder.model.ts";
import { SearchedArtifact } from "@models/searchedArtifact.model.ts";

/**
 * Properties
 */
export type ArtifactsTabContentProps = {
    group: GroupMetaData;
    onCreateArtifact: () => void;
    onDeleteArtifact: (artifact: SearchedArtifact, successCallback?: () => void) => void;
    onViewArtifact: (artifact: SearchedArtifact) => void;
};

/**
 * Models the content of the Artifact Info tab.
 */
export const ArtifactsTabContent: FunctionComponent<ArtifactsTabContentProps> = (props: ArtifactsTabContentProps) => {
    const [isLoading, setLoading] = useState<boolean>(true);
    const [isError, setError] = useState<boolean>(false);
    const [paging, setPaging] = useState<Paging>({
        page: 1,
        pageSize: 20
    });
    const [sortBy, setSortBy] = useState(ArtifactSortBy.artifactId);
    const [sortOrder, setSortOrder] = useState(SortOrder.asc);
    const [results, setResults] = useState<ArtifactSearchResults>({
        count: 0,
        artifacts: []
    });

    const groups: GroupsService = useGroupsService();
    const logger: LoggerService = useLoggerService();

    const refresh = (): void => {
        setLoading(true);

        groups.getGroupArtifacts(props.group.groupId, sortBy, sortOrder, paging).then(sr => {
            setResults(sr);
            setLoading(false);
        }).catch(error => {
            logger.error(error);
            setLoading(false);
            setError(true);
        });
    };

    const onDelete = (artifact: SearchedArtifact): void => {
        props.onDeleteArtifact(artifact, () => {
            setTimeout(refresh, 100);
        });
    };

    useEffect(() => {
        refresh();
    }, [props.group, paging, sortBy, sortOrder]);

    const onSort = (by: ArtifactSortBy, order: SortOrder): void => {
        setSortBy(by);
        setSortOrder(order);
    };

    const toolbar = (
        <ArtifactsTabToolbar results={results} paging={paging} onPageChange={setPaging} onCreateArtifact={props.onCreateArtifact} />
    );

    const emptyState = (
        <EmptyState variant={EmptyStateVariant.sm}>
            <EmptyStateIcon icon={PlusCircleIcon}/>
            <Title headingLevel="h5" size="lg">No artifacts found</Title>
            <EmptyStateBody>
                There are currently no artifacts in this group.  Create some artifacts in the group to view them here.
            </EmptyStateBody>
            <EmptyStateFooter>
                <EmptyStateActions>
                    <IfAuth isDeveloper={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button className="empty-btn-create" variant="primary"
                                data-testid="empty-btn-create" onClick={props.onCreateArtifact}>Create artifact</Button>
                        </IfFeature>
                    </IfAuth>
                </EmptyStateActions>
            </EmptyStateFooter>
        </EmptyState>
    );

    return (
        <div className="artifacts-tab-content">
            <div className="artifacts-toolbar-and-table">
                <ListWithToolbar toolbar={toolbar}
                    emptyState={emptyState}
                    filteredEmptyState={emptyState}
                    isLoading={isLoading}
                    isError={isError}
                    isFiltered={false}
                    isEmpty={results.count === 0}
                >
                    <ArtifactsTable
                        artifacts={results.artifacts}
                        onSort={onSort}
                        sortBy={sortBy}
                        sortOrder={sortOrder}
                        onView={props.onViewArtifact}
                        onDelete={onDelete}
                    />
                </ListWithToolbar>
            </div>
        </div>
    );

};
