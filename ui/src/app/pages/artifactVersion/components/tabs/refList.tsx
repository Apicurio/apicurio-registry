import React, { FunctionComponent, useEffect, useState } from "react";
import { ThProps } from "@patternfly/react-table/src/components/TableComposable/Th";
import { ArtifactReference } from "../../../../../models/artifactReference.model";
import { ResponsiveTable } from "@rhoas/app-services-ui-components";
import { Link } from "react-router-dom";

export type SortDirection = "asc" | "desc";
export type SortBy = "name" | "group" | "id";

export interface ReferencesSort {
    by: SortBy;
    direction: SortDirection;
}

export type ReferenceListProps = {
    references: ArtifactReference[];
    sort: ReferencesSort;
    onSort: (sort: ReferencesSort) => void;
}

let rowIndex: number = 0;
const nextRowIndex = (): number => {
    return rowIndex++;
};

export const ReferenceList: FunctionComponent<ReferenceListProps> = (
    { references, sort, onSort }: ReferenceListProps) => {

    const [sortByIndex, setSortByIndex] = useState<number>();

    const columns: any[] = [
        { index: 0, id: "name", label: "Reference Name", width: 45, sortable: true },
        { index: 1, id: "group", label: "Group", width: 20, sortable: true },
        { index: 2, id: "id", label: "ID", width: 25, sortable: true },
        { index: 3, id: "version", label: "Version", width: 10, sortable: false },
    ];

    const renderColumnData = (column: ArtifactReference, colIndex: number): React.ReactNode => {
        // Name.
        if (colIndex === 0) {
            return (
                <span>{ column.name }</span>
            );
        }
        // Group.
        if (colIndex === 1) {
            return (
                <span>{ column.groupId }</span>
            );
        }
        // ID
        if (colIndex === 2) {
            const groupId: string = column.groupId == null ? "default" : column.groupId;
            const link: string = `/artifacts/${ encodeURIComponent(groupId)}/${ encodeURIComponent(column.artifactId) }/versions/${ encodeURIComponent(column.version) }`;
            return (
                <Link className="reference-id" to={ link }>{ column.artifactId }</Link>
            );
        }
        // Version.
        if (colIndex === 3) {
            return (
                <span>{ column.version }</span>
            );
        }
        return (
            <span />
        );
    };

    const sortParams = (column: any): ThProps["sort"] | undefined => {
        return column.sortable ? {
            sortBy: {
                index: sortByIndex,
                direction: sort.direction
            },
            onSort: (_event, index, direction) => {
                const byn: SortBy[] = ["name", "group", "id"];
                const sort: ReferencesSort = {
                    by: byn[index],
                    direction
                };
                onSort(sort);
            },
            columnIndex: column.index
        } : undefined;
    };

    useEffect(() => {
        if (sort.by === "name") {
            setSortByIndex(0);
        }
        if (sort.by === "group") {
            setSortByIndex(1);
        }
        if (sort.by === "id") {
            setSortByIndex(2);
        }
    }, [sort]);

    return (
        <div className="design-list">
            <ResponsiveTable
                ariaLabel="list of designs"
                columns={columns}
                data={references}
                expectedLength={references.length}
                minimumColumnWidth={350}
                renderHeader={({ column, Th }) => (
                    <Th sort={sortParams(column)}
                        className="design-list-header"
                        key={`header-${column.id}`}
                        width={column.width}
                        modifier="truncate">{column.label}</Th>
                )}
                renderCell={({ row, colIndex, Td }) => (
                    <Td className="design-list-cell" key={`cell-${colIndex}-${nextRowIndex()}`}
                        style={{ maxWidth: "0", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}
                        children={renderColumnData(row as ArtifactReference, colIndex) as any} />
                )}
            />
        </div>
    );
};
