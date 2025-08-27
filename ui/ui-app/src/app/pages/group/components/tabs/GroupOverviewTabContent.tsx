import { FunctionComponent, useEffect, useState } from "react";
import "./GroupOverviewTabContent.css";
import "@app/styles/empty.css";
import { IfAuth, IfFeature } from "@app/components";
import {
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
    EmptyStateIcon,
    EmptyStateVariant,
    Flex,
    FlexItem,
    Icon,
    Label,
    Title,
    Truncate
} from "@patternfly/react-core";
import { OutlinedFolderIcon, PencilAltIcon, PlusCircleIcon } from "@patternfly/react-icons";
import { FromNow, If, ListWithToolbar } from "@apicurio/common-ui-components";
import { isStringEmptyOrUndefined } from "@utils/string.utils.ts";
import {
    ArtifactSearchResults,
    ArtifactSortBy,
    ArtifactSortByObject,
    GroupMetaData,
    SearchedArtifact,
    SortOrder,
    SortOrderObject
} from "@sdk/lib/generated-client/models";
import { labelsToAny } from "@utils/rest.utils.ts";
import { Paging } from "@models/Paging.ts";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { GroupArtifactsTabToolbar } from "@app/pages/group/components/tabs/GroupArtifactsTabToolbar.tsx";
import { GroupArtifactsTable } from "@app/pages/group/components/tabs/GroupArtifactsTable.tsx";

/**
 * Properties
 */
export type GroupOverviewTabContentProps = {
    group: GroupMetaData;
    onEditMetaData: () => void;
    onChangeOwner: () => void;
    onCreateArtifact: () => void;
    onDeleteArtifact: (artifact: SearchedArtifact, successCallback?: () => void) => void;
    onViewArtifact: (artifact: SearchedArtifact) => void;
};

/**
 * Models the content of the Group Overview tab.
 */
export const GroupOverviewTabContent: FunctionComponent<GroupOverviewTabContentProps> = (props: GroupOverviewTabContentProps) => {
    const [isLoading, setLoading] = useState<boolean>(true);
    const [isError, setError] = useState<boolean>(false);
    const [paging, setPaging] = useState<Paging>({
        page: 1,
        pageSize: 20
    });
    const [sortBy, setSortBy] = useState<ArtifactSortBy>(ArtifactSortByObject.ArtifactId);
    const [sortOrder, setSortOrder] = useState<SortOrder>(SortOrderObject.Asc);
    const [results, setResults] = useState<ArtifactSearchResults>({
        count: 0,
        artifacts: []
    });

    const groups: GroupsService = useGroupsService();
    const logger: LoggerService = useLoggerService();

    const description = (): string => {
        return props.group.description || "No description";
    };

    const labels: any = labelsToAny(props.group.labels);

    const refresh = (): void => {
        setLoading(true);

        groups.getGroupArtifacts(props.group.groupId!, sortBy, sortOrder, paging).then(sr => {
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
        <GroupArtifactsTabToolbar results={results} paging={paging} onPageChange={setPaging} onCreateArtifact={props.onCreateArtifact} />
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
        <div className="group-tab-content">
            <div className="group-basics">
                <Card>
                    <CardTitle>
                        <div className="title-and-type">
                            <Flex>
                                <FlexItem className="type"><Icon><OutlinedFolderIcon /></Icon></FlexItem>
                                <FlexItem className="title">Group metadata</FlexItem>
                                <FlexItem className="actions" align={{ default: "alignRight" }}>
                                    <IfAuth isDeveloper={true}>
                                        <IfFeature feature="readOnly" isNot={true}>
                                            <Button id="edit-action"
                                                data-testid="group-btn-edit"
                                                onClick={props.onEditMetaData}
                                                variant="link"><PencilAltIcon />{" "}Edit</Button>
                                        </IfFeature>
                                    </IfAuth>
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
                                    data-testid="group-details-description"
                                    className={!props.group.description ? "empty-state-text" : ""}
                                >
                                    { description() }
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Created</DescriptionListTerm>
                                <DescriptionListDescription data-testid="group-details-created-on">
                                    <FromNow date={props.group.createdOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <If condition={!isStringEmptyOrUndefined(props.group.owner)}>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Owner</DescriptionListTerm>
                                    <DescriptionListDescription data-testid="group-details-created-by">
                                        <span>{props.group.owner}</span>
                                        <span>
                                            <IfAuth isAdminOrOwner={true} owner={props.group.owner}>
                                                <IfFeature feature="readOnly" isNot={true}>
                                                    <Button id="edit-action"
                                                        data-testid="group-btn-change-owner"
                                                        onClick={props.onChangeOwner}
                                                        variant="link"><PencilAltIcon /></Button>
                                                </IfFeature>
                                            </IfAuth>
                                        </span>
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            </If>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Modified</DescriptionListTerm>
                                <DescriptionListDescription data-testid="group-details-modified-on">
                                    <FromNow date={props.group.modifiedOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Labels</DescriptionListTerm>
                                {!labels || !Object.keys(labels).length ?
                                    <DescriptionListDescription data-testid="group-details-labels" className="empty-state-text">No labels</DescriptionListDescription> :
                                    <DescriptionListDescription data-testid="group-details-labels">{Object.entries(labels).map(([key, value]) =>
                                        <Label key={`label-${key}`} color="purple" style={{ marginBottom: "2px", marginRight: "5px" }}>
                                            <Truncate className="label-truncate" content={`${key}=${value}`} />
                                        </Label>
                                    )}</DescriptionListDescription>
                                }
                            </DescriptionListGroup>
                        </DescriptionList>
                    </CardBody>
                </Card>
            </div>
            <div className="group-artifacts">
                <ListWithToolbar toolbar={toolbar}
                    emptyState={emptyState}
                    filteredEmptyState={emptyState}
                    isLoading={isLoading}
                    isError={isError}
                    isFiltered={false}
                    isEmpty={results.count === 0}
                >
                    <GroupArtifactsTable
                        artifacts={results.artifacts!}
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
