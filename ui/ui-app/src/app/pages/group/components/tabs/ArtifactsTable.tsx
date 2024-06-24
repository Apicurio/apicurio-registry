import React, { FunctionComponent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { SortByDirection, ThProps } from "@patternfly/react-table";
import { FromNow, ObjectDropdown, ResponsiveTable } from "@apicurio/common-ui-components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { ArtifactDescription, ArtifactTypeIcon } from "@app/components";
import { shash } from "@utils/string.utils.ts";
import { Truncate } from "@patternfly/react-core";
import {
    ArtifactSortBy,
    ArtifactSortByObject,
    SearchedArtifact,
    SortOrder,
    SortOrderObject
} from "@sdk/lib/generated-client/models";

export type ArtifactsTableProps = {
    artifacts: SearchedArtifact[];
    sortBy: ArtifactSortBy;
    sortOrder: SortOrder;
    onSort: (by: ArtifactSortBy, order: SortOrder) => void;
    onView: (artifact: SearchedArtifact) => void;
    onDelete: (artifact: SearchedArtifact) => void;
}
type ArtifactAction = {
    label: string;
    testId: string;
    onClick: () => void;
};

type ArtifactActionSeparator = {
    isSeparator: true;
};

export const ArtifactsTable: FunctionComponent<ArtifactsTableProps> = (props: ArtifactsTableProps) => {
    const [sortByIndex, setSortByIndex] = useState<number>();

    const appNavigation: AppNavigation = useAppNavigation();

    const columns: any[] = [
        { index: 0, id: "artifactId", label: "Artifact Id", width: 40, sortable: true, sortBy: ArtifactSortByObject.ArtifactId },
        { index: 1, id: "type", label: "Type", width: 15, sortable: true, sortBy: ArtifactSortByObject.ArtifactType },
        { index: 2, id: "createdOn", label: "Created on", width: 15, sortable: true, sortBy: ArtifactSortByObject.CreatedOn },
        { index: 3, id: "modifiedOn", label: "Modified on", width: 15, sortable: true, sortBy: ArtifactSortByObject.ModifiedOn },
    ];

    const idAndName = (artifact: SearchedArtifact): string => {
        return artifact.artifactId + (artifact.name ? ` (${artifact.name})` : "");
    };

    const renderColumnData = (column: SearchedArtifact, colIndex: number): React.ReactNode => {
        // Name.
        if (colIndex === 0) {
            return (
                <div>
                    <Link className="artifact-title"
                        style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}
                        to={appNavigation.createLink(`/explore/${encodeURIComponent(column.groupId as string)}/${encodeURIComponent(column.artifactId!)}`)}
                    >
                        <Truncate content={idAndName(column)} />
                    </Link>
                    <ArtifactDescription className="artifact-description" style={{ overflow: "hidden", textOverflow: "hidden", whiteSpace: "nowrap", fontSize: "14px" }}
                        description={column.description}
                        truncate={true} />
                </div>
            );
        }
        // Type.
        if (colIndex === 1) {
            return (
                <ArtifactTypeIcon artifactType={column.artifactType!} />
            );
        }
        // Created on.
        if (colIndex === 2) {
            return (
                <FromNow date={column.createdOn} />
            );
        }
        // Modified on.
        if (colIndex === 3) {
            return (
                <FromNow date={column.modifiedOn} />
            );
        }
    };

    const actionsFor = (artifact: SearchedArtifact): (ArtifactAction | ArtifactActionSeparator)[] => {
        const ahash: number = shash(artifact.artifactId!);
        return [
            { label: "View artifact", onClick: () => props.onView(artifact), testId: `view-artifact-${ahash}` },
            { isSeparator: true },
            { label: "Delete artifact", onClick: () => props.onDelete(artifact), testId: `delete-artifact-${ahash}` }
        ];
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
        if (props.sortBy === ArtifactSortByObject.ArtifactId) {
            setSortByIndex(0);
        }
        if (props.sortBy === ArtifactSortByObject.ArtifactType) {
            setSortByIndex(1);
        }
        if (props.sortBy === ArtifactSortByObject.CreatedOn) {
            setSortByIndex(2);
        }
        if (props.sortBy === ArtifactSortByObject.ModifiedOn) {
            setSortByIndex(3);
        }
    }, [props.sortBy]);

    return (
        <div className="artifacts-table">
            <ResponsiveTable
                ariaLabel="table of artifacts"
                columns={columns}
                data={props.artifacts}
                expectedLength={props.artifacts.length}
                minimumColumnWidth={350}
                onRowClick={(row) => {
                    console.log(row);
                }}
                renderHeader={({ column, Th }) => (
                    <Th sort={sortParams(column)}
                        className="artifacts-table-header"
                        key={`header-${column.id}`}
                        width={column.width}
                        modifier="truncate">{column.label}</Th>
                )}
                renderCell={({ row, colIndex, Td }) => (
                    <Td className="artifacts-table-cell" key={`cell-${colIndex}-${shash(row.artifactId!)}`}
                        style={{ maxWidth: "0", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}
                        children={renderColumnData(row as SearchedArtifact, colIndex) as any} />
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
                        testId={`api-actions-${shash(row.artifactId!)}`}
                        popperProps={{
                            position: "right"
                        }}
                    />
                )}
            />
        </div>
    );
};
