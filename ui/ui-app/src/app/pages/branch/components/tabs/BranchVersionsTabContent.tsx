import { FunctionComponent, useEffect, useState } from "react";
import "./BranchVersionsTabContent.css";
import "@app/styles/empty.css";
import { ListWithToolbar } from "@apicurio/common-ui-components";
import { Paging } from "@models/paging.model.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import {
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    EmptyStateIcon,
    EmptyStateVariant,
    Title
} from "@patternfly/react-core";
import { PlusCircleIcon } from "@patternfly/react-icons";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import {
    ArtifactMetaData,
    BranchMetaData,
    SearchedVersion,
    VersionSearchResults
} from "@sdk/lib/generated-client/models";
import { BranchVersionsTabToolbar } from "@app/pages/branch/components/tabs/BranchVersionsTabToolbar.tsx";
import { BranchVersionsTable } from "@app/pages/branch/components/tabs/BranchVersionsTable.tsx";

/**
 * Properties
 */
export type BranchVersionsTabContentProps = {
    artifact: ArtifactMetaData;
    branch: BranchMetaData;
    onViewVersion: (version: SearchedVersion) => void;
};

/**
 * Models the content of the Version Info tab.
 */
export const BranchVersionsTabContent: FunctionComponent<BranchVersionsTabContentProps> = (props: BranchVersionsTabContentProps) => {
    const [isLoading, setLoading] = useState<boolean>(true);
    const [isError, setError] = useState<boolean>(false);
    const [paging, setPaging] = useState<Paging>({
        page: 1,
        pageSize: 20
    });
    const [results, setResults] = useState<VersionSearchResults>({
        count: 0,
        versions: []
    });

    const groups: GroupsService = useGroupsService();
    const logger: LoggerService = useLoggerService();

    const refresh = (): void => {
        setLoading(true);

        groups.getArtifactBranchVersions(props.artifact.groupId!, props.artifact.artifactId!, props.branch.branchId!, paging).then(sr => {
            setResults(sr);
            setLoading(false);
        }).catch(error => {
            logger.error(error);
            setLoading(false);
            setError(true);
        });
    };

    useEffect(() => {
        refresh();
    }, [props.artifact, paging]);

    const toolbar = (
        <BranchVersionsTabToolbar results={results} paging={paging} onPageChange={setPaging} />
    );

    const emptyState = (
        <EmptyState variant={EmptyStateVariant.sm}>
            <EmptyStateIcon icon={PlusCircleIcon}/>
            <Title headingLevel="h5" size="lg">No versions found</Title>
            <EmptyStateBody>
                There are currently no versions in this branch.  Add some versions to the branch to view them here.
            </EmptyStateBody>
            <EmptyStateFooter>
                <EmptyStateActions>
                </EmptyStateActions>
            </EmptyStateFooter>
        </EmptyState>
    );

    return (
        <div className="branch-versions-tab-content">
            <div className="branch-versions-toolbar-and-table">
                <ListWithToolbar
                    toolbar={toolbar}
                    emptyState={emptyState}
                    filteredEmptyState={emptyState}
                    isLoading={isLoading}
                    isError={isError}
                    isFiltered={false}
                    isEmpty={results.count === 0}
                >
                    <BranchVersionsTable
                        artifact={props.artifact}
                        branch={props.branch}
                        versions={results.versions!}
                        onView={props.onViewVersion}
                    />
                </ListWithToolbar>
            </div>
        </div>
    );

};
