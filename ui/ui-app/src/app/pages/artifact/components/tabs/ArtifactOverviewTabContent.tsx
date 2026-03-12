import React, { FunctionComponent, useEffect, useState } from "react";
import "./ArtifactOverviewTabContent.css";
import "@app/styles/empty.css";
import { ArtifactTypeIcon, IfAuth, IfFeature, VersionCompareModal } from "@app/components";
import {
    Button,
    Card,
    CardBody,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Drawer,
    DrawerContent,
    DrawerContentBody,
    DrawerHead,
    DrawerPanelContent,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    EmptyStateVariant,
    Flex,
    FlexItem,
    Label,
    Truncate
} from "@patternfly/react-core";
import { PencilAltIcon, PlusCircleIcon } from "@patternfly/react-icons";
import { FromNow, If, ListWithToolbar } from "@apicurio/common-ui-components";
import { isStringEmptyOrUndefined } from "@utils/string.utils.ts";
import {
    ArtifactMetaData,
    Rule,
    SearchedVersion,
    SortOrder,
    SortOrderObject,
    VersionSearchResults,
    VersionSortBy,
    VersionSortByObject
} from "@sdk/lib/generated-client/models";
import { labelsToAny } from "@utils/rest.utils.ts";
import { Paging } from "@models/Paging.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { ArtifactVersionsToolbar, VersionsTable } from "@app/pages";
import { FilterBy, SearchFilter, SearchService, useSearchService } from "@services/useSearchService.ts";

/**
 * Properties
 */
export type ArtifactOverviewTabContentProps = {
    artifact: ArtifactMetaData;
    rules: Rule[];
    onCreateVersion: () => void;
    onViewVersion: (version: SearchedVersion) => void;
    onEditDraft: (version: SearchedVersion) => void;
    onDeleteVersion: (version: SearchedVersion, deleteSuccessCallback: () => void) => void;
    onAddVersionToBranch: (version: SearchedVersion) => void;
    onEditMetaData: () => void;
    onChangeOwner: () => void;
};

/**
 * Models the content of the Artifact Info tab.
 */
export const ArtifactOverviewTabContent: FunctionComponent<ArtifactOverviewTabContentProps> = (props: ArtifactOverviewTabContentProps) => {
    const [isLoading, setLoading] = useState<boolean>(true);
    const [isError, setError] = useState<boolean>(false);
    const [paging, setPaging] = useState<Paging>({
        page: 1,
        pageSize: 20
    });
    const [sortBy, setSortBy] = useState<VersionSortBy>(VersionSortByObject.GlobalId);
    const [sortOrder, setSortOrder] = useState<SortOrder>(SortOrderObject.Asc);
    const [filterValue, setFilterValue] = useState<string>("");
    const [results, setResults] = useState<VersionSearchResults>({
        count: 0,
        versions: []
    });
    const [isExpanded] = useState(true);
    const [selectedVersions, setSelectedVersions] = useState<SearchedVersion[]>([]);
    const [isCompareModalOpen, setIsCompareModalOpen] = useState<boolean>(false);

    const drawerRef: any = React.useRef<HTMLDivElement>(null);

    const search: SearchService = useSearchService();
    const logger: LoggerService = useLoggerService();

    const handleSelectVersion = (version: SearchedVersion, isSelected: boolean): void => {
        if (isSelected) {
            if (selectedVersions.length < 2) {
                setSelectedVersions([...selectedVersions, version]);
            }
        } else {
            setSelectedVersions(selectedVersions.filter(v => v.version !== version.version));
        }
    };

    const handleClearSelection = (): void => {
        setSelectedVersions([]);
    };

    const handleCompareVersions = (): void => {
        if (selectedVersions.length === 2) {
            setIsCompareModalOpen(true);
        }
    };

    const handleCloseCompareModal = (): void => {
        setIsCompareModalOpen(false);
    };

    const description = (): string => {
        return props.artifact.description || "No description";
    };

    const artifactName = (): string => {
        return props.artifact.name || "No name";
    };

    const labels: any = labelsToAny(props.artifact.labels);

    const refresh = (): void => {
        setLoading(true);

        const filters: SearchFilter[] = [
            { by: FilterBy.groupId, value: props.artifact.groupId || "default" },
            { by: FilterBy.artifactId, value: props.artifact.artifactId! }
        ];

        if (filterValue && filterValue.trim() !== "") {
            filters.push({ by: FilterBy.version, value: filterValue.trim() });
        }

        search.searchVersions(filters, sortBy, sortOrder, paging).then(sr => {
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
    }, [props.artifact, paging, sortBy, sortOrder, filterValue]);

    const toolbar = (
        <ArtifactVersionsToolbar
            results={results}
            paging={paging}
            selectedVersions={selectedVersions}
            onPageChange={setPaging}
            onFilterChange={setFilterValue}
            onCompareVersions={handleCompareVersions}
            onClearSelection={handleClearSelection} />
    );

    const emptyState = (
        <EmptyState titleText="No versions found" icon={PlusCircleIcon} variant={EmptyStateVariant.sm}>
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

    const panelContent = (
        <DrawerPanelContent isResizable={true} defaultSize={"500px"} minSize={"300px"}>
            <DrawerHead className="__drawer-head">
                <span tabIndex={isExpanded ? 0 : -1} ref={drawerRef}>
                    <div className="artifact-basics">
                        <div className="title-and-type">
                            <Flex>
                                <FlexItem className="title">Artifact metadata</FlexItem>
                                <FlexItem className="actions" align={{ default: "alignRight" }}>
                                    <IfAuth isDeveloper={true} owner={props.artifact.owner}>
                                        <IfFeature feature="readOnly" isNot={true}>
                                            <Button icon={<PencilAltIcon />} id="edit-action"
                                                data-testid="artifact-btn-edit"
                                                onClick={props.onEditMetaData}
                                                style={{ padding: "0" }}
                                                variant="link">{" "}Edit</Button>
                                        </IfFeature>
                                    </IfAuth>
                                </FlexItem>
                            </Flex>
                        </div>
                        <DescriptionList className="metaData" isCompact={true}>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Name</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="artifact-details-name"
                                    className={!props.artifact.name ? "empty-state-text" : ""}
                                >
                                    { artifactName() }
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Description</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="artifact-details-description"
                                    className={!props.artifact.description ? "empty-state-text" : ""}
                                >
                                    { description() }
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Type</DescriptionListTerm>
                                <DescriptionListDescription data-testid="artifact-details-type">
                                    <ArtifactTypeIcon artifactType={props.artifact.artifactType!} />
                                    { props.artifact.artifactType }
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Created</DescriptionListTerm>
                                <DescriptionListDescription data-testid="artifact-details-created-on">
                                    <FromNow date={props.artifact.createdOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <If condition={!isStringEmptyOrUndefined(props.artifact.owner)}>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Owner</DescriptionListTerm>
                                    <DescriptionListDescription data-testid="artifact-details-created-by">
                                        <span>{props.artifact.owner}</span>
                                        <span>
                                            <IfAuth isAdminOrOwner={true} owner={props.artifact.owner}>
                                                <IfFeature feature="readOnly" isNot={true}>
                                                    <Button icon={<PencilAltIcon />} id="edit-action"
                                                        data-testid="artifact-btn-change-owner"
                                                        onClick={props.onChangeOwner}
                                                        variant="link"></Button>
                                                </IfFeature>
                                            </IfAuth>
                                        </span>
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            </If>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Modified</DescriptionListTerm>
                                <DescriptionListDescription data-testid="artifact-details-modified-on">
                                    <FromNow date={props.artifact.modifiedOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Labels</DescriptionListTerm>
                                {!labels || !Object.keys(labels).length ?
                                    <DescriptionListDescription data-testid="artifact-details-labels" className="empty-state-text">No labels</DescriptionListDescription> :
                                    <DescriptionListDescription data-testid="artifact-details-labels">{Object.entries(labels).map(([key, value]) =>
                                        <Label key={`label-${key}`} color="purple" style={{ marginBottom: "2px", marginRight: "5px" }}>
                                            <Truncate className="label-truncate" content={`${key}=${value}`} />
                                        </Label>
                                    )}</DescriptionListDescription>
                                }
                            </DescriptionListGroup>
                        </DescriptionList>
                    </div>
                </span>
            </DrawerHead>
        </DrawerPanelContent>
    );

    const drawerContent = (
        <div className="artifact-versions">
            <div className="title-and-type">
                <Flex>
                    <FlexItem className="title">Versions in artifact</FlexItem>
                    <FlexItem className="actions" align={{ default: "alignRight" }}>
                        <IfAuth isDeveloper={true}>
                            <IfFeature feature="readOnly" isNot={true}>
                                <Button className="btn-header-create-version" size="sm" data-testid="btn-create-version"
                                    variant="primary" onClick={props.onCreateVersion}>Create version</Button>
                            </IfFeature>
                        </IfAuth>
                    </FlexItem>
                </Flex>
            </div>
            <div className="version-list">
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
                        selectedVersions={selectedVersions}
                        onSort={onSort}
                        sortBy={sortBy}
                        sortOrder={sortOrder}
                        onDelete={onDeleteVersion}
                        onView={props.onViewVersion}
                        onAddToBranch={props.onAddVersionToBranch}
                        onEditDraft={props.onEditDraft}
                        onSelectVersion={handleSelectVersion}
                    />
                </ListWithToolbar>
            </div>
        </div>
    );

    return (
        <div className="artifact-overview-tab-content">
            <Card variant="secondary">
                <CardBody style={{ padding: "0" }}>
                    <Drawer isExpanded={true} onExpand={() => {}} isInline={true} position="start">
                        <DrawerContent panelContent={panelContent} style={{ backgroundColor: "white" }}>
                            <DrawerContentBody hasPadding={false}>{drawerContent}</DrawerContentBody>
                        </DrawerContent>
                    </Drawer>
                </CardBody>
            </Card>
            <VersionCompareModal
                isOpen={isCompareModalOpen}
                groupId={props.artifact.groupId || null}
                artifactId={props.artifact.artifactId!}
                version1={selectedVersions.length >= 1 ? selectedVersions[0] : null}
                version2={selectedVersions.length >= 2 ? selectedVersions[1] : null}
                onClose={handleCloseCompareModal}
            />
        </div>
    );

};
