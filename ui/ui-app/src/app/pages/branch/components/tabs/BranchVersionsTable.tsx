import React, { FunctionComponent } from "react";
import "./BranchVersionsTable.css";
import { Link } from "react-router-dom";
import { FromNow, If, ObjectDropdown, ResponsiveTable } from "@apicurio/common-ui-components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { shash } from "@utils/string.utils.ts";
import { ArtifactDescription } from "@app/components";
import { ArtifactMetaData, BranchMetaData, SearchedVersion } from "@sdk/lib/generated-client/models";

export type BranchVersionsTableProps = {
    artifact: ArtifactMetaData;
    branch: BranchMetaData;
    versions: SearchedVersion[];
    onView: (version: SearchedVersion) => void;
}
type VersionAction = {
    label: string;
    testId: string;
    onClick: () => void;
};

type VersionActionSeparator = {
    isSeparator: true;
};

export const BranchVersionsTable: FunctionComponent<BranchVersionsTableProps> = (props: BranchVersionsTableProps) => {
    const appNavigation: AppNavigation = useAppNavigation();

    const columns: any[] = [
        { index: 0, id: "version", label: "Version", width: 40, sortable: false },
        { index: 1, id: "globalId", label: "Global Id", width: 10, sortable: false },
        { index: 2, id: "contentId", label: "Content Id", width: 10, sortable: false },
        { index: 3, id: "createdOn", label: "Created on", width: 15, sortable: false },
    ];

    const renderColumnData = (column: SearchedVersion, colIndex: number): React.ReactNode => {
        // Name.
        if (colIndex === 0) {
            const groupId: string = encodeURIComponent(props.artifact.groupId || "default");
            const artifactId: string = encodeURIComponent(props.artifact.artifactId!);
            const version: string = encodeURIComponent(column.version!);
            return (
                <div>
                    <Link className="version-title"
                        style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", textDecoration: "none" }}
                        to={appNavigation.createLink(`/explore/${groupId}/${artifactId}/versions/${version}`)}
                    >
                        <span>{ column.version }</span>
                        <If condition={column.name != "" && column.name !== undefined && column.name !== null}>
                            <span style={{ marginLeft: "10px" }}>({column.name})</span>
                        </If>
                    </Link>
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
        return [
            { label: "View version", onClick: () => props.onView(version), testId: `view-version-${vhash}` }
        ];
    };

    return (
        <div className="branch-versions-table">
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
                    <Th
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
