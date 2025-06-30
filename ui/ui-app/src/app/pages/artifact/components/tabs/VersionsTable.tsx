import React, { FunctionComponent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { SortByDirection, ThProps } from "@patternfly/react-table";
import { FromNow, If, ObjectDropdown, ResponsiveTable } from "@apicurio/common-ui-components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { shash } from "@utils/string.utils.ts";
import { ArtifactDescription } from "@app/components";
import {
    ArtifactMetaData,
    SearchedVersion,
    SortOrder,
    SortOrderObject,
    VersionSortBy,
    VersionSortByObject
} from "@sdk/lib/generated-client/models";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { Flex, FlexItem, Label } from "@patternfly/react-core";
import { UserService, useUserService } from "@services/useUserService.ts";

export type VersionsTableProps = {
    artifact: ArtifactMetaData;
    versions: SearchedVersion[];
    sortBy: VersionSortBy;
    sortOrder: SortOrder;
    onSort: (by: VersionSortBy, order: SortOrder) => void;
    onView: (version: SearchedVersion) => void;
    onAddToBranch: (version: SearchedVersion) => void;
    onDelete: (version: SearchedVersion) => void;
}
type VersionAction = {
    label: string;
    testId: string;
    onClick: () => void;
};

type VersionActionSeparator = {
    isSeparator: true;
};

export const VersionsTable: FunctionComponent<VersionsTableProps> = (props: VersionsTableProps) => {
    const [sortByIndex, setSortByIndex] = useState<number>();

    const appNavigation: AppNavigation = useAppNavigation();
    const config: ConfigService = useConfigService();
    const user: UserService = useUserService();

    const columns: any[] = [
        { index: 0, id: "version", label: "Version", width: 40, sortable: true, sortBy: VersionSortByObject.Version },
        { index: 1, id: "globalId", label: "Global Id", width: 10, sortable: true, sortBy: VersionSortByObject.GlobalId },
        { index: 2, id: "contentId", label: "Content Id", width: 10, sortable: false },
        { index: 3, id: "createdOn", label: "Created on", width: 15, sortable: true, sortBy: VersionSortByObject.CreatedOn },
    ];

    const renderColumnData = (column: SearchedVersion, colIndex: number): React.ReactNode => {
        // Name.
        if (colIndex === 0) {
            const groupId: string = encodeURIComponent(props.artifact.groupId || "default");
            const artifactId: string = encodeURIComponent(props.artifact.artifactId!);
            const version: string = encodeURIComponent(column.version!);
            return (
                <div>
                    <Flex>
                        <FlexItem>
                            <Link className="version-title"
                                style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", textDecoration: "none" }}
                                to={appNavigation.createLink(`/explore/${groupId}/${artifactId}/versions/${version}`)}
                            >
                                <span>{ column.version }</span>
                                <If condition={column.name != "" && column.name !== undefined && column.name !== null}>
                                    <span style={{ marginLeft: "10px" }}>({column.name})</span>
                                </If>
                            </Link>
                        </FlexItem>
                        <FlexItem>
                            <If condition={column.state === "DRAFT"}>
                                <Label color="grey">Draft</Label>
                            </If>
                            <If condition={column.state === "DEPRECATED"}>
                                <Label color="orange">Deprecated</Label>
                            </If>
                            <If condition={column.state === "DISABLED"}>
                                <Label color="red">Disabled</Label>
                            </If>
                        </FlexItem>
                    </Flex>
                    <ArtifactDescription className="version-description" style={{ overflow: "hidden", textOverflow: "hidden", whiteSpace: "nowrap", fontSize: "14px" }}
                        description={column.description}
                        truncate={true} />
                </div>
            );
        }
        // Global id.
        if (colIndex === 1) {
            return (
                <span>{ column.globalId }</span>
            );
        }
        // Global id.
        if (colIndex === 2) {
            return (
                <span>{ column.contentId }</span>
            );
        }
        // Created on.
        if (colIndex === 3) {
            return (
                <FromNow date={column.createdOn} />
            );
        }
    };

    const actionsFor = (version: SearchedVersion): (VersionAction | VersionActionSeparator)[] => {
        const vhash: number = shash(version.version!);
        // TODO hide/show actions based on user role
        const actions: (VersionAction | VersionActionSeparator)[] = [
            { label: "View version", onClick: () => props.onView(version), testId: `view-version-${vhash}` },
        ];
        if (!config.featureReadOnly() && user.isUserDeveloper(props.artifact.owner)) {
            actions.push(
                { label: "Add to branch", onClick: () => props.onAddToBranch(version), testId: `add-to-branch-version-${vhash}` },
            );

            if (config.featureDeleteVersion()) {
                actions.push(
                    { isSeparator: true },
                );
                actions.push(
                    { label: "Delete version", onClick: () => props.onDelete(version), testId: `delete-version-${vhash}` }
                );
            }
        }
        return actions;
    };

    const sortParams = (column: any): ThProps["sort"] | undefined => {
        return column.sortable ? {
            sortBy: {
                index: sortByIndex,
                direction: props.sortOrder
            },
            onSort: (_event, index, direction) => {
                props.onSort(columns[index].sortBy, direction === SortByDirection.asc ? SortOrderObject.Asc : SortOrderObject.Desc);
            },
            columnIndex: column.index
        } : undefined;
    };

    useEffect(() => {
        if (props.sortBy === VersionSortByObject.Version) {
            setSortByIndex(0);
        }
        if (props.sortBy === VersionSortByObject.GlobalId) {
            setSortByIndex(1);
        }
        if (props.sortBy === VersionSortByObject.CreatedOn) {
            setSortByIndex(3);
        }
    }, [props.sortBy]);

    return (
        <div className="versions-table">
            <ResponsiveTable
                ariaLabel="table of versions"
                columns={columns}
                data={props.versions}
                expectedLength={props.versions.length}
                minimumColumnWidth={350}
                onRowClick={(row) => {
                    console.log(row);
                }}
                renderHeader={({ column, Th }) => (
                    <Th sort={sortParams(column)}
                        className="versions-table-header"
                        key={`header-${column.id}`}
                        width={column.width}
                        modifier="truncate">{column.label}</Th>
                )}
                renderCell={({ row, colIndex, Td }) => (
                    <Td className="versions-table-cell" key={`cell-${colIndex}-${shash(row.version!)}`}
                        style={{ maxWidth: "0", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}
                        children={renderColumnData(row as SearchedVersion, colIndex) as any} />
                )}
                renderActions={({ row }) => (
                    <ObjectDropdown
                        items={actionsFor(row)}
                        isKebab={true}
                        label="Actions"
                        itemToString={item => item.label}
                        itemToTestId={item => item.testId}
                        itemIsDivider={item => item.isSeparator}
                        onSelect={item => item.onClick()}
                        testId={`version-actions-${shash(row.version!)}`}
                        popperProps={{
                            position: "right"
                        }}
                    />
                )}
            />
        </div>
    );
};
