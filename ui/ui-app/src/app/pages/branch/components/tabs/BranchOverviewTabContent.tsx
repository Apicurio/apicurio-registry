import { FunctionComponent, useEffect, useState } from "react";
import "./BranchOverviewTabContent.css";
import "@app/styles/empty.css";
import { IfAuth, IfFeature } from "@app/components";
import {
    Alert,
    Button,
    Card,
    CardBody,
    CardTitle,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Divider,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    EmptyStateVariant,
    Flex,
    FlexItem,
    Title
} from "@patternfly/react-core";
import { CodeBranchIcon, PencilAltIcon, PlusCircleIcon } from "@patternfly/react-icons";
import { FromNow, If, ListWithToolbar } from "@apicurio/common-ui-components";
import {
    ArtifactMetaData,
    BranchMetaData,
    SearchedVersion,
    VersionSearchResults
} from "@sdk/lib/generated-client/models";
import { BranchVersionsTable, BranchVersionsToolbar } from "@app/pages";
import { Paging } from "@models/Paging.ts";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";

/**
 * Properties
 */
export type BranchOverviewTabContentProps = {
    artifact: ArtifactMetaData;
    branch: BranchMetaData;
    onEditMetaData: () => void;
    onViewVersion: (version: SearchedVersion) => void;
};

/**
 * Models the content of the Branch Info (overview) tab.
 */
export const BranchOverviewTabContent: FunctionComponent<BranchOverviewTabContentProps> = (props: BranchOverviewTabContentProps) => {
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

    const description = (): string => {
        return props.branch.description || "No description";
    };

    const toolbar = (
        <BranchVersionsToolbar results={results} paging={paging} onPageChange={setPaging} />
    );

    const emptyState = (
        <EmptyState titleText={<Title headingLevel="h5" size="lg">No versions found</Title>} icon={PlusCircleIcon} variant={EmptyStateVariant.sm}>
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
        <div className="branch-overview-tab-content">
            <div className="branch-basics">
                <Card>
                    <CardTitle>
                        <div className="title-and-type">
                            <Flex>
                                <FlexItem className="type"><CodeBranchIcon /></FlexItem>
                                <FlexItem className="title">Branch metadata</FlexItem>
                                <FlexItem className="actions" align={{ default: "alignRight" }}>
                                    <If condition={!(props.branch.systemDefined || false)}>
                                        <IfAuth isDeveloper={true} owner={props.artifact.owner}>
                                            <IfFeature feature="readOnly" isNot={true}>
                                                <Button icon={<PencilAltIcon />} id="edit-action"
                                                    data-testid="branch-btn-edit"
                                                    onClick={props.onEditMetaData}
                                                    variant="link">{" "}Edit</Button>
                                            </IfFeature>
                                        </IfAuth>
                                    </If>
                                </FlexItem>
                            </Flex>
                        </div>
                    </CardTitle>
                    <Divider />
                    <CardBody>
                        <DescriptionList className="metaData" isCompact={true}>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Description</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="branch-details-description"
                                    className={!props.branch.description ? "empty-state-text" : ""}
                                >
                                    { description() }
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Created</DescriptionListTerm>
                                <DescriptionListDescription data-testid="branch-details-created-on">
                                    <FromNow date={props.branch.createdOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <If condition={props.branch.owner !== undefined && props.branch.owner !== ""}>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Owner</DescriptionListTerm>
                                    <DescriptionListDescription data-testid="branch-details-created-by">
                                        <span>{props.branch.owner}</span>
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            </If>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Modified</DescriptionListTerm>
                                <DescriptionListDescription data-testid="branch-details-modified-on">
                                    <FromNow date={props.branch.modifiedOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                        </DescriptionList>
                        <If condition={props.branch.systemDefined || false}>
                            <Alert variant="info" title="Note: This branch was system generated" ouiaId="InfoAlert" />
                        </If>
                    </CardBody>
                </Card>
            </div>
            <div className="branch-versions">
                <Card>
                    <CardTitle>
                        <div className="title-and-type">
                            <Flex>
                                <FlexItem className="title">Versions in branch</FlexItem>
                            </Flex>
                        </div>
                    </CardTitle>
                    <Divider />
                    <CardBody>
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
                    </CardBody>
                </Card>
            </div>
        </div>
    );

};
