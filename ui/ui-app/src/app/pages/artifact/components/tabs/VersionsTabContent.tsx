import { FunctionComponent, useEffect, useState } from "react";
import "./VersionsTabContent.css";
import "@app/styles/empty.css";
import { ListWithToolbar } from "@apicurio/common-ui-components";
import { Paging } from "@models/paging.model.ts";
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
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { VersionsTable, VersionsTabToolbar } from "@app/pages/artifact";
import {
    ArtifactMetaData,
    SearchedVersion,
    SortOrder,
    SortOrderObject,
    VersionSearchResults,
    VersionSortBy,
    VersionSortByObject
} from "@sdk/lib/generated-client/models";

/**
 * Properties
 */
export type VersionsTabContentProps = {
    artifact: ArtifactMetaData;
    onCreateVersion: () => void;
    onViewVersion: (version: SearchedVersion) => void;
    onDeleteVersion: (version: SearchedVersion, deleteSuccessCallback: () => void) => void;
    onAddVersionToBranch: (version: SearchedVersion) => void;
};

/**
 * Models the content of the Version Info tab.
 */
export const VersionsTabContent: FunctionComponent<VersionsTabContentProps> = (props: VersionsTabContentProps) => {
    const [isLoading, setLoading] = useState<boolean>(true);
    const [isError, setError] = useState<boolean>(false);
    const [paging, setPaging] = useState<Paging>({
        page: 1,
        pageSize: 20
    });
    const [sortBy, setSortBy] = useState<VersionSortBy>(VersionSortByObject.GlobalId);
    const [sortOrder, setSortOrder] = useState<SortOrder>(SortOrderObject.Asc);
    const [results, setResults] = useState<VersionSearchResults>({
        count: 0,
        versions: []
    });

    const groups: GroupsService = useGroupsService();
    const logger: LoggerService = useLoggerService();

    const refresh = (): void => {
        setLoading(true);

        groups.getArtifactVersions(props.artifact.groupId!, props.artifact.artifactId!, sortBy, sortOrder, paging).then(sr => {
            setResults(sr);
            setLoading(false);
        }).catch(error => {
            logger.error(error);
            setLoading(false);
            setError(true);
        });
    };

    const onSort = (by: VersionSortBy, order: SortOrder): void => {
        setSortBy(by);
        setSortOrder(order);
    };

    const onDeleteVersion = (version: SearchedVersion): void => {
        props.onDeleteVersion(version, () => {
            setTimeout(refresh, 100);
        });
    };

    useEffect(() => {
        refresh();
    }, [props.artifact, paging, sortBy, sortOrder]);

    const toolbar = (
        <VersionsTabToolbar results={results} paging={paging} onPageChange={setPaging} onCreateVersion={props.onCreateVersion} />
    );

    const emptyState = (
        <EmptyState variant={EmptyStateVariant.sm}>
            <EmptyStateIcon icon={PlusCircleIcon}/>
            <Title headingLevel="h5" size="lg">No versions found</Title>
            <EmptyStateBody>
                There are currently no versions in this artifact.  Create some versions in the artifact to view them here.
            </EmptyStateBody>
            <EmptyStateFooter>
                <EmptyStateActions>
                    <IfAuth isDeveloper={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button className="empty-btn-create" variant="primary"
                                data-testid="empty-btn-create" onClick={props.onCreateVersion}>Create version</Button>
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
                    <VersionsTable
                        artifact={props.artifact}
                        versions={results.versions!}
                        onSort={onSort}
                        sortBy={sortBy}
                        sortOrder={sortOrder}
                        onDelete={onDeleteVersion}
                        onView={props.onViewVersion}
                        onAddToBranch={props.onAddVersionToBranch}
                    />
                </ListWithToolbar>
            </div>
        </div>
    );

};
